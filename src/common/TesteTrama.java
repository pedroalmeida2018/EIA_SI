package common;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;

public class TesteTrama {
    public static void main(String[] args) throws Exception {
        byte[] original = "ola relay".getBytes(StandardCharsets.UTF_8);

        // Escrever para a memoria
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        Trama.escrever(new DataOutputStream(buffer), (byte) 1, original);

        // Ler de volta
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(buffer.toByteArray()));
        Trama lida = Trama.ler(in);

        System.out.println("Tipo: " + lida.tipo);
        System.out.println("Corpo: " + new String(lida.corpo, StandardCharsets.UTF_8));
    }
}