package common;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/*
 * AES-256-GCM: da confidencialidade E integridade (a tag de 128 bits deteta
 * qualquer alteracao ao texto cifrado ou aos dados associados - AAD).
 * Regra de ouro do GCM: nunca repetir o IV com a mesma chave -> IV aleatorio
 * de 12 bytes em cada mensagem.
 */
public final class Cifra {

    public static final int TAM_IV = 12;
    private static final int TAM_TAG_BITS = 128;
    private static final SecureRandom RNG = new SecureRandom();

    private Cifra() {}

    public static byte[] novoIv() {
        byte[] iv = new byte[TAM_IV];
        RNG.nextBytes(iv);
        return iv;
    }

    public static byte[] cifrar(byte[] chave, byte[] iv, byte[] claro, byte[] aad)
            throws GeneralSecurityException {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(chave, "AES"), new GCMParameterSpec(TAM_TAG_BITS, iv));
        c.updateAAD(aad);
        return c.doFinal(claro);
    }

    // Lanca AEADBadTagException se o texto cifrado, o IV ou o AAD tiverem sido alterados.
    public static byte[] decifrar(byte[] chave, byte[] iv, byte[] cifrado, byte[] aad)
            throws GeneralSecurityException {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(chave, "AES"), new GCMParameterSpec(TAM_TAG_BITS, iv));
        c.updateAAD(aad);
        return c.doFinal(cifrado);
    }
}
