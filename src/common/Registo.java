package common;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.Arrays;

/*
 * Registo de um utilizador: nome + chave publica Ed25519 + chave publica X25519,
 * tudo assinado com a chave privada Ed25519 (auto-assinado).
 *
 * Formato (bytes exatos que sao assinados e enviados):
 *   writeUTF(nome) | [len][pubEd25519] | [len][pubX25519] | [len][assinatura]
 *   assinatura = Ed25519( tudo o que vem antes dela )
 *
 * O relay guarda estes bytes tal como vieram e devolve-os na descoberta. Assim,
 * o cliente que procura alguem verifica ELE PROPRIO a assinatura, sem ter de
 * confiar que o relay a verificou.
 */
public final class Registo {

    public static final int MAX_CHAVE = 256;
    public static final int MAX_ASSINATURA = 128;

    public final String nome;
    public final PublicKey chaveAssinatura; // Ed25519
    public final PublicKey chaveAcordo;     // X25519
    private final byte[] bytes;             // registo original, tal como foi assinado

    private Registo(String nome, PublicKey chaveAssinatura, PublicKey chaveAcordo, byte[] bytes) {
        this.nome = nome;
        this.chaveAssinatura = chaveAssinatura;
        this.chaveAcordo = chaveAcordo;
        this.bytes = bytes;
    }

    public byte[] bytes() {
        return bytes.clone();
    }

    // Nomes curtos e so com caracteres simples: evita nomes enganadores
    // (espacos, caracteres invisiveis, letras de outros alfabetos parecidas).
    public static void validarNome(String nome) {
        if (nome == null || !nome.matches("[A-Za-z0-9_-]{1,32}")) {
            throw new IllegalArgumentException("Nome invalido (1-32 caracteres: letras, digitos, _ ou -)");
        }
    }

    // Lado do cliente: constroi os bytes do registo e assina-os.
    public static byte[] criar(String nome, KeyPair parAssinatura, KeyPair parAcordo)
            throws IOException, GeneralSecurityException {
        validarNome(nome);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream d = new DataOutputStream(buffer);
        d.writeUTF(nome);
        Bytes.escrever(d, parAssinatura.getPublic().getEncoded());
        Bytes.escrever(d, parAcordo.getPublic().getEncoded());

        byte[] assinados = buffer.toByteArray();
        Bytes.escrever(d, Chaves.assinar(parAssinatura.getPrivate(), assinados));
        return buffer.toByteArray();
    }

    // Lado de quem recebe (relay ou cliente): le, valida e verifica a assinatura.
    // Lanca excecao se alguma coisa estiver errada; nunca devolve um registo invalido.
    public static Registo ler(byte[] corpo) throws IOException, GeneralSecurityException {
        DataInputStream d = new DataInputStream(new ByteArrayInputStream(corpo));
        String nome = d.readUTF();
        validarNome(nome);
        byte[] pubEd = Bytes.ler(d, MAX_CHAVE);
        byte[] pubX = Bytes.ler(d, MAX_CHAVE);

        int fimAssinados = corpo.length - d.available(); // onde comeca a assinatura
        byte[] assinatura = Bytes.ler(d, MAX_ASSINATURA);
        if (d.available() != 0) {
            throw new IOException("Bytes a mais no registo");
        }

        PublicKey chaveEd = Chaves.publicaAssinatura(pubEd);
        PublicKey chaveX = Chaves.publicaAcordo(pubX);

        byte[] assinados = Arrays.copyOf(corpo, fimAssinados);
        if (!Chaves.verificar(chaveEd, assinados, assinatura)) {
            throw new GeneralSecurityException("Assinatura do registo invalida");
        }
        return new Registo(nome, chaveEd, chaveX, corpo.clone());
    }
}
