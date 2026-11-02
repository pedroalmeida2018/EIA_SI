package relay;

import common.Tipos;
import common.Trama;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class RelayMain {

    public static final int PORTO = 5000;

    public static void main(String[] args) throws IOException {
        try (ServerSocket servidor = new ServerSocket(PORTO)) {
            System.out.println("Relay a escutar no porto " + PORTO);
            while (true) {
                Socket socket = servidor.accept();          // fica a espera de um cliente
                Thread t = new Thread(() -> atender(socket)); // uma thread por cliente
                t.start();
            }
        }
    }

    private static void atender(Socket socket) {
        String origem = socket.getRemoteSocketAddress().toString();
        System.out.println("Cliente ligado: " + origem);

        try (socket;
             DataInputStream in = new DataInputStream(socket.getInputStream());
             DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

            while (true) {
                Trama trama = Trama.ler(in);

                if (trama.tipo == Tipos.OLA) {
                    String nome = new String(trama.corpo, StandardCharsets.UTF_8);
                    System.out.println("OLA recebido de " + nome);
                    byte[] resposta = ("ola, " + nome).getBytes(StandardCharsets.UTF_8);
                    Trama.escrever(out, Tipos.OLA_RESP, resposta);
                } else {
                    Trama.escrever(out, Tipos.ERRO, "tipo desconhecido".getBytes(StandardCharsets.UTF_8));
                }
            }
        } catch (IOException e) {
            // acontece quando o cliente se desliga: e normal
            System.out.println("Cliente desligado: " + origem);
        }
    }
}