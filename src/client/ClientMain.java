package client;

import common.Tipos;
import common.Trama;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ClientMain {

    public static void main(String[] args) throws Exception {
        String nome = args.length > 0 ? args[0] : "anonimo";

        try (Socket socket = new Socket("localhost", 5000);
             DataInputStream in = new DataInputStream(socket.getInputStream());
             DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

            Trama.escrever(out, Tipos.OLA, nome.getBytes(StandardCharsets.UTF_8));
            Trama resposta = Trama.ler(in);

            System.out.println("Resposta (tipo " + resposta.tipo + "): "
                    + new String(resposta.corpo, StandardCharsets.UTF_8));
        }
    }
}