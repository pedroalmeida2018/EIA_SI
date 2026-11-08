package relay;

import common.Bytes;
import common.Registo;
import common.Tipos;
import common.Trama;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

/*
 * Relay: regista utilizadores, responde a pedidos de descoberta e reencaminha
 * envelopes cifrados. Nunca tem acesso as chaves privadas nem ao texto das mensagens.
 */
public class RelayMain {

    public static final int PORTO_PREDEFINIDO = 5000;

    // nome -> registo (chaves publicas). Partilhado por todas as threads.
    private static final ConcurrentHashMap<String, Registo> utilizadores = new ConcurrentHashMap<>();

    // nome -> ligacao do cliente que esta ligado neste momento.
    private static final ConcurrentHashMap<String, Ligacao> ligados = new ConcurrentHashMap<>();

    /*
     * Envolve o fluxo de saida de um cliente. 'synchronized' porque varias threads
     * podem escrever para o mesmo cliente ao mesmo tempo (a dele, ao responder,
     * e a de outro cliente, ao entregar-lhe uma mensagem). Sem isto, os bytes de
     * duas tramas podiam ficar misturados.
     */
    static final class Ligacao {
        private final DataOutputStream out;

        Ligacao(DataOutputStream out) {
            this.out = out;
        }

        synchronized void enviar(byte tipo, byte[] corpo) throws IOException {
            Trama.escrever(out, tipo, corpo);
        }

        void erro(String mensagem) throws IOException {
            enviar(Tipos.ERRO, mensagem.getBytes(StandardCharsets.UTF_8));
        }
    }

    public static void main(String[] args) throws IOException {
        int porto = args.length > 0 ? Integer.parseInt(args[0]) : PORTO_PREDEFINIDO;
        try (ServerSocket servidor = new ServerSocket(porto)) {
            System.out.println("Relay a escutar no porto " + porto);
            while (true) {
                Socket socket = servidor.accept();
                new Thread(() -> atender(socket)).start(); // uma thread por cliente
            }
        }
    }

    private static void atender(Socket socket) {
        String origem = socket.getRemoteSocketAddress().toString();
        System.out.println("Cliente ligado: " + origem);
        String meuNome = null; // fica preenchido depois de um REGISTO valido
        Ligacao lig = null;

        try (socket;
             DataInputStream in = new DataInputStream(socket.getInputStream());
             DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

            lig = new Ligacao(out);
            while (true) {
                Trama trama = Trama.ler(in);
                switch (trama.tipo) {
                    case Tipos.REGISTO -> {
                        if (meuNome != null) {
                            lig.erro("Esta ligacao ja esta registada como " + meuNome);
                        } else {
                            meuNome = registar(trama.corpo, lig);
                        }
                    }
                    case Tipos.PROCURAR -> procurar(trama.corpo, lig);
                    case Tipos.ENVIAR -> {
                        if (meuNome == null) {
                            lig.erro("Tem de se registar primeiro");
                        } else {
                            reencaminhar(meuNome, trama.corpo, lig);
                        }
                    }
                    default -> lig.erro("Tipo de trama desconhecido: " + trama.tipo);
                }
            }
        } catch (IOException e) {
            // normal quando o cliente se desliga (ou envia uma trama mal formada)
        } finally {
            if (meuNome != null) {
                ligados.remove(meuNome, lig); // so remove se ainda for esta ligacao
            }
            System.out.println("Cliente desligado: " + origem + (meuNome != null ? " (" + meuNome + ")" : ""));
        }
    }

    // Devolve o nome registado, ou null se o registo foi recusado.
    private static String registar(byte[] corpo, Ligacao lig) throws IOException {
        Registo novo;
        try {
            novo = Registo.ler(corpo); // valida o formato e verifica a assinatura
        } catch (Exception e) {
            lig.erro("Registo invalido: " + e.getMessage());
            return null;
        }

        // Verificar-e-inserir numa so operacao atomica: evita que duas threads
        // registem o mesmo nome ao mesmo tempo com chaves diferentes.
        boolean[] recusado = {false};
        utilizadores.compute(novo.nome, (nome, atual) -> {
            if (atual != null && !Arrays.equals(atual.chaveAssinatura.getEncoded(),
                                                novo.chaveAssinatura.getEncoded())) {
                recusado[0] = true;
                return atual; // o nome continua a pertencer ao dono original
            }
            return novo;
        });
        if (recusado[0]) {
            lig.erro("Nome ja registado com outra chave");
            return null;
        }

        ligados.put(novo.nome, lig); // se ja estava ligado noutra ligacao, esta passa a ser a ativa
        System.out.println("Registado: " + novo.nome);
        lig.enviar(Tipos.REGISTO_OK, new byte[0]);
        return novo.nome;
    }

    private static void procurar(byte[] corpo, Ligacao lig) throws IOException {
        String nome = new String(corpo, StandardCharsets.UTF_8);
        Registo r = utilizadores.get(nome);
        if (r == null) {
            lig.erro("Utilizador desconhecido: " + nome);
        } else {
            // Devolve os bytes originais assinados: o cliente verifica a assinatura sozinho.
            lig.enviar(Tipos.PROCURAR_RESP, r.bytes());
        }
    }

    // Corpo de ENVIAR: writeUTF(destinatario) | [len][envelope]
    private static void reencaminhar(String remetente, byte[] corpo, Ligacao lig) throws IOException {
        String destino;
        byte[] envelope;
        try {
            DataInputStream d = new DataInputStream(new ByteArrayInputStream(corpo));
            destino = d.readUTF();
            envelope = Bytes.ler(d, Trama.MAX_CORPO);
        } catch (IOException e) {
            lig.erro("Pedido de envio mal formado");
            return;
        }

        Ligacao ligDestino = ligados.get(destino);
        if (ligDestino == null) {
            lig.erro("Destinatario desligado: " + destino);
            return;
        }
        try {
            ligDestino.enviar(Tipos.ENTREGA, envelope); // o relay nao consegue ler o envelope
        } catch (IOException e) {
            lig.erro("Falha ao entregar a " + destino);
            return;
        }
        System.out.println("Reencaminhada mensagem " + remetente + " -> " + destino
                + " (" + envelope.length + " bytes cifrados)");
        lig.enviar(Tipos.ENVIAR_OK, new byte[0]);
    }
}
