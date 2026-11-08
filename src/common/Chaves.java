package common;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import javax.crypto.KeyAgreement;

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

    // Acordo de chaves X25519: os dois lados chegam ao mesmo segredo sem o enviar.
    // ATENCAO: o resultado nao deve ser usado diretamente como chave -> passar por HKDF.
    public static byte[] segredoPartilhado(PrivateKey minhaPrivada, PublicKey publicaDoOutro)
            throws GeneralSecurityException {
        KeyAgreement ka = KeyAgreement.getInstance("X25519");
        ka.init(minhaPrivada);
        ka.doPhase(publicaDoOutro, true);
        return ka.generateSecret();
    }

    // Reconstroi chaves publicas a partir dos bytes recebidos pela rede (formato X.509).
    public static PublicKey publicaAssinatura(byte[] codificada) throws GeneralSecurityException {
        return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(codificada));
    }

    public static PublicKey publicaAcordo(byte[] codificada) throws GeneralSecurityException {
        return KeyFactory.getInstance("X25519").generatePublic(new X509EncodedKeySpec(codificada));
    }

    // Reconstroi chaves privadas lidas do disco (formato PKCS#8).
    public static PrivateKey privadaAssinatura(byte[] codificada) throws GeneralSecurityException {
        return KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(codificada));
    }

    public static PrivateKey privadaAcordo(byte[] codificada) throws GeneralSecurityException {
        return KeyFactory.getInstance("X25519").generatePrivate(new PKCS8EncodedKeySpec(codificada));
    }
}
