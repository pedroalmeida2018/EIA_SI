package common;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Arrays;

/*
 * Envelope de uma mensagem (cifrado ponta a ponta; o relay so o reencaminha).
 *
 * Formato:
 *   cabecalho = writeUTF(remetente) | writeUTF(destinatario) | writeLong(instante)
 *   envelope  = cabecalho | [len][iv] | [len][cifrado] | [len][assinatura]
 *
 *   chave      = SHA-256(segredo X25519(remetente, destinatario) | "|rem|dest")
 *   cifrado    = AES-256-GCM(chave, iv, texto, AAD = cabecalho)
 *   assinatura = Ed25519(remetente, tudo o que vem antes dela)
 *
 * - Confidencialidade: so quem tem uma das privadas X25519 obtem a chave.
 * - Integridade: tag GCM (cobre texto e cabecalho) + assinatura.
 * - Autenticidade: assinatura Ed25519 do remetente.
 */
public final class Mensagem {

    private static final int MAX_CIFRADO = Trama.MAX_CORPO;

    public final String remetente;
    public final String destinatario;
    public final long instante;

    private final byte[] cabecalho;
    private final byte[] iv;
    private final byte[] cifrado;
    private final byte[] assinados;
    private final byte[] assinatura;

    private Mensagem(String remetente, String destinatario, long instante, byte[] cabecalho,
                     byte[] iv, byte[] cifrado, byte[] assinados, byte[] assinatura) {
        this.remetente = remetente;
        this.destinatario = destinatario;
        this.instante = instante;
        this.cabecalho = cabecalho;
        this.iv = iv;
        this.cifrado = cifrado;
        this.assinados = assinados;
        this.assinatura = assinatura;
    }

    // Remetente: cifra e assina. Devolve os bytes do envelope.
    public static byte[] selar(String remetente, KeyPair minhaAssinatura, PrivateKey meuAcordo,
                               Registo destino, String texto) throws IOException, GeneralSecurityException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream d = new DataOutputStream(buffer);
        d.writeUTF(remetente);
        d.writeUTF(destino.nome);
        d.writeLong(System.currentTimeMillis());
        byte[] cabecalho = buffer.toByteArray();

        byte[] chave = derivarChave(meuAcordo, destino.chaveAcordo, remetente, destino.nome);
        byte[] iv = Cifra.novoIv();
        byte[] cifrado = Cifra.cifrar(chave, iv, texto.getBytes(StandardCharsets.UTF_8), cabecalho);

        Bytes.escrever(d, iv);
        Bytes.escrever(d, cifrado);
        byte[] assinados = buffer.toByteArray();
        Bytes.escrever(d, Chaves.assinar(minhaAssinatura.getPrivate(), assinados));
        return buffer.toByteArray();
    }

    // Destinatario, passo 1: le o envelope (ainda sem verificar nada), para saber quem o enviou.
    public static Mensagem ler(byte[] envelope) throws IOException {
        DataInputStream d = new DataInputStream(new ByteArrayInputStream(envelope));
        String remetente = d.readUTF();
        String destinatario = d.readUTF();
        long instante = d.readLong();
        byte[] cabecalho = Arrays.copyOf(envelope, envelope.length - d.available());

        byte[] iv = Bytes.ler(d, Cifra.TAM_IV);
        if (iv.length != Cifra.TAM_IV) {
            throw new IOException("IV com tamanho errado");
        }
        byte[] cifrado = Bytes.ler(d, MAX_CIFRADO);
        byte[] assinados = Arrays.copyOf(envelope, envelope.length - d.available());
        byte[] assinatura = Bytes.ler(d, Registo.MAX_ASSINATURA);
        if (d.available() != 0) {
            throw new IOException("Bytes a mais no envelope");
        }
        return new Mensagem(remetente, destinatario, instante, cabecalho, iv, cifrado, assinados, assinatura);
    }

    // Destinatario, passo 2: verifica a assinatura e decifra. Lanca excecao se algo falhar.
    public String abrir(String meuNome, PrivateKey meuAcordo, Registo registoRemetente)
            throws GeneralSecurityException {
        if (!destinatario.equals(meuNome)) {
            throw new GeneralSecurityException("Mensagem nao e para mim");
        }
        if (!registoRemetente.nome.equals(remetente)) {
            throw new GeneralSecurityException("Registo nao corresponde ao remetente");
        }
        if (!Chaves.verificar(registoRemetente.chaveAssinatura, assinados, assinatura)) {
            throw new GeneralSecurityException("Assinatura da mensagem invalida");
        }
        byte[] chave = derivarChave(meuAcordo, registoRemetente.chaveAcordo, remetente, destinatario);
        byte[] claro = Cifra.decifrar(chave, iv, cifrado, cabecalho);
        return new String(claro, StandardCharsets.UTF_8);
    }

    // Os dois lados calculam a mesma chave: X25519 da o mesmo segredo.
    // O segredo do X25519 nao deve ser usado diretamente como chave AES, por isso
    // passa por SHA-256 juntamente com a direcao (rem -> dest): cada sentido tem a sua chave.
    private static byte[] derivarChave(PrivateKey meuAcordo, PublicKey acordoDoOutro,
                                       String remetente, String destinatario) throws GeneralSecurityException {
        byte[] segredo = Chaves.segredoPartilhado(meuAcordo, acordoDoOutro);
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        sha.update(segredo);
        sha.update(("|" + remetente + "|" + destinatario).getBytes(StandardCharsets.UTF_8));
        return sha.digest(); // 32 bytes -> AES-256
    }
}
