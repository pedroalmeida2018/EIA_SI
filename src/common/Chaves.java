package common;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;

public class Chaves {

    public static KeyPair gerarParAssinatura() throws GeneralSecurityException {
        return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    }

    public static KeyPair gerarParAcordo() throws GeneralSecurityException {
        return KeyPairGenerator.getInstance("X25519").generateKeyPair();
    }

    public static byte[] assinar(PrivateKey chavePrivada, byte[] dados) throws GeneralSecurityException {
        Signature s = Signature.getInstance("Ed25519");
        s.initSign(chavePrivada);
        s.update(dados);
        return s.sign();
    }

    // Devolve false se a assinatura for invalida (nunca lanca excecao por isso).
    public static boolean verificar(PublicKey chavePublica, byte[] dados, byte[] assinatura) {
        try {
            Signature s = Signature.getInstance("Ed25519");
            s.initVerify(chavePublica);
            s.update(dados);
            return s.verify(assinatura);
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    // Reconstroi uma chave publica Ed25519 a partir dos bytes recebidos pela rede.
    public static PublicKey publicaAssinatura(byte[] codificada) throws GeneralSecurityException {
        return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(codificada));
    }

    // Idem para X25519.
    public static PublicKey publicaAcordo(byte[] codificada) throws GeneralSecurityException {
        return KeyFactory.getInstance("X25519").generatePublic(new X509EncodedKeySpec(codificada));
    }
}