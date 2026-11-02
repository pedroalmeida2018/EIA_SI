package common;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/* Classe que representa uma trama de dados. */
public class Trama {

    // Limite maximo do corpo. Sem isto, um atacante podia anunciar
    // um tamanho gigante e fazer o relay ficar sem memoria.
    public static final int MAX_CORPO = 64 * 1024;

    // Tipo da trama (1 byte) e corpo da trama (até MAX_CORPO bytes).
    public final byte tipo;
    public final byte[] corpo;

    public Trama(byte tipo, byte[] corpo) {
        this.tipo = tipo;
        this.corpo = corpo;
    }

    // Escreve uma trama no fluxo de saida.
    public static void escrever(DataOutputStream out, byte tipo, byte[] corpo) throws IOException {
        if (corpo.length > MAX_CORPO) {
            throw new IOException("Corpo demasiado grande: " + corpo.length);
        }
        out.writeByte(tipo);
        out.writeInt(corpo.length);
        out.write(corpo);
        out.flush();
    }

    // Le uma trama do fluxo de entrada (fica a esperar ate chegar).
    public static Trama ler(DataInputStream in) throws IOException {
        byte tipo = in.readByte();
        int tamanho = in.readInt();
        if (tamanho < 0 || tamanho > MAX_CORPO) {
            throw new IOException("Tamanho invalido: " + tamanho);
        }
        byte[] corpo = new byte[tamanho];
        // Le o corpo da trama. O metodo readFully garante que le TODOS os bytes, ou lança uma excecao.
        in.readFully(corpo);
        return new Trama(tipo, corpo);
    }
}