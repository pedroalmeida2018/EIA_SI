package common;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/* Funcoes auxiliares para escrever/ler campos com prefixo de comprimento. */
public final class Bytes {

    private Bytes() {}

    // Escreve [tamanho (4 bytes)][bytes].
    public static void escrever(DataOutputStream out, byte[] dados) throws IOException {
        out.writeInt(dados.length);
        out.write(dados);
    }

    // Le [tamanho][bytes], recusando tamanhos negativos ou acima de 'max'.
    // Sem este limite, um atacante podia pedir new byte[2_000_000_000] e
    // provocar um OutOfMemoryError (que nao e apanhado por catch (Exception)).
    public static byte[] ler(DataInputStream in, int max) throws IOException {
        int tamanho = in.readInt();
        if (tamanho < 0 || tamanho > max) {
            throw new IOException("Tamanho de campo invalido: " + tamanho);
        }
        byte[] dados = new byte[tamanho];
        in.readFully(dados);
        return dados;
    }
}