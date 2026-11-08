package client;

import common.Bytes;
import common.Chaves;
import common.Mensagem;
import common.Registo;
import common.Tipos;
import common.Trama;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;


 //Threads:
 //main: le comandos do teclado e faz pedidos ao relay (um de cada vez);
 //leitora: le TODAS as tramas que chegam do relay. As respostas a pedidos
 //vao para a fila 'respostas'; as mensagens recebidas (ENTREGA) vao para a
 //thread 'trabalho', que as decifra (e pode precisar de pedir as chaves do
 //remetente ao relay, por isso nao pode ser a propria leitora a faze-lo).
 
public class ClientMain {

    private static final DateTimeFormatter HORA =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final String nome;
    private final KeyPair parAssinatura; // Ed25519
    private final KeyPair parAcordo;     // X25519
    private final DataOutputStream out;
    private final BlockingQueue<Trama> respostas = new LinkedBlockingQueue<>();
    private final ExecutorService trabalho = Executors.newSingleThreadExecutor();

    // Contactos ja obtidos (nome -> registo verificado).
    private final ConcurrentHashMap<String, Registo> contactos = new ConcurrentHashMap<>();

    private ClientMain(String nome, DataOutputStream out) throws Exception {
        this.nome = nome;
        this.parAssinatura = Chaves.gerarParAssinatura();
        this.parAcordo = Chaves.gerarParAcordo();
        this.out = out;
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Uso: java -cp out client.ClientMain <nome> [host] [porto]");
            return;
        }
        String nome = args[0];
        Registo.validarNome(nome);
        String host = args.length > 1 ? args[1] : "localhost";
        int porto = args.length > 2 ? Integer.parseInt(args[2]) : 5000;

        try (Socket socket = new Socket(host, porto);
             DataInputStream in = new DataInputStream(socket.getInputStream());
             DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

            ClientMain cliente = new ClientMain(nome, out);
            cliente.iniciarLeitora(in);
            if (cliente.registar()) {
                cliente.ciclo();
            }
            cliente.trabalho.shutdownNow();
        }
    }

    // comunicacao com o relay

    // Envia um pedido e espera pela resposta. 'synchronized' garante um pedido de
    // cada vez, por isso a resposta que chega e sempre a deste pedido.
    private synchronized Trama pedido(byte tipo, byte[] corpo) throws IOException, InterruptedException {
        Trama.escrever(out, tipo, corpo);
        Trama resposta = respostas.poll(10, TimeUnit.SECONDS);
        if (resposta == null) {
            throw new IOException("Sem resposta do relay");
        }
        return resposta;
    }

    private void iniciarLeitora(DataInputStream in) {
        Thread leitora = new Thread(() -> {
            try {
                while (true) {
                    Trama t = Trama.ler(in);
                    if (t.tipo == Tipos.ENTREGA) {
                        trabalho.submit(() -> receber(t.corpo));
                    } else {
                        respostas.put(t);
                    }
                }
            } catch (IOException | InterruptedException e) {
                System.out.println("Ligacao ao relay terminada.");
                System.exit(0);
            }
        });
        leitora.setDaemon(true);
        leitora.start();
    }

    private boolean registar() throws Exception {
        byte[] corpo = Registo.criar(nome, parAssinatura, parAcordo);
        Trama r = pedido(Tipos.REGISTO, corpo);
        if (r.tipo == Tipos.REGISTO_OK) {
            System.out.println("Registado como '" + nome + "'.");
            System.out.println("Comandos: procurar <nome> | enviar <nome> <texto> | sair");
            return true;
        }
        System.out.println("Registo recusado: " + texto(r));
        return false;
    }

    // Descoberta: pede o registo ao relay e verifica-o localmente.
    private Registo obterContacto(String outro) throws Exception {
        Registo conhecido = contactos.get(outro);
        if (conhecido != null) {
            return conhecido;
        }
        Trama r = pedido(Tipos.PROCURAR, outro.getBytes(StandardCharsets.UTF_8));
        if (r.tipo != Tipos.PROCURAR_RESP) {
            throw new IOException(texto(r));
        }
        Registo reg = Registo.ler(r.corpo); // verifica a auto-assinatura (nao confia no relay)
        if (!reg.nome.equals(outro)) {
            throw new IOException("O relay devolveu o registo de outro utilizador");
        }
        contactos.put(outro, reg);
        return reg;
    }

    private void enviar(String destino, String texto) throws Exception {
        Registo reg = obterContacto(destino);
        byte[] envelope = Mensagem.selar(nome, parAssinatura, parAcordo.getPrivate(), reg, texto);

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream d = new DataOutputStream(buffer);
        d.writeUTF(destino);
        Bytes.escrever(d, envelope);

        Trama r = pedido(Tipos.ENVIAR, buffer.toByteArray());
        if (r.tipo == Tipos.ENVIAR_OK) {
            System.out.println("[enviada para " + destino + "]");
        } else {
            System.out.println("Nao enviada: " + texto(r));
        }
    }

    // Corre na thread 'trabalho'.
    private void receber(byte[] envelope) {
        try {
            Mensagem m = Mensagem.ler(envelope);
            Registo reg = obterContacto(m.remetente);
            String texto = m.abrir(nome, parAcordo.getPrivate(), reg);
            System.out.println("[" + HORA.format(Instant.ofEpochMilli(m.instante)) + "] "
                    + m.remetente + ": " + texto);
        } catch (Exception e) {
            System.out.println("!! Mensagem rejeitada: " + e.getMessage());
        }
    }

    // ---------- interface de texto ----------

    private void ciclo() throws IOException {
        BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String linha;
        while ((linha = teclado.readLine()) != null) {
            String[] partes = linha.trim().split(" ", 3);
            try {
                switch (partes[0]) {
                    case "procurar" -> {
                        if (partes.length < 2) { System.out.println("Uso: procurar <nome>"); break; }
                        Registo reg = obterContacto(partes[1]);
                        System.out.println(reg.nome + " encontrado (registo com assinatura valida).");
                    }
                    case "enviar" -> {
                        if (partes.length < 3) { System.out.println("Uso: enviar <nome> <texto>"); break; }
                        enviar(partes[1], partes[2]);
                    }
                    case "sair" -> { return; }
                    case "" -> { }
                    default -> System.out.println("Comandos: procurar <nome> | enviar <nome> <texto> | sair");
                }
            } catch (Exception e) {
                System.out.println("Erro: " + e.getMessage());
            }
        }
    }

    private static String texto(Trama t) {
        return new String(t.corpo, StandardCharsets.UTF_8);
    }
}
