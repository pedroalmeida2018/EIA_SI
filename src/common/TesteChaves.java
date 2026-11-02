package common;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Arrays;
import javax.crypto.KeyAgreement;

public class TesteChaves {

    public static void main(String[] args) throws Exception {
        byte[] dados = "mensagem de teste".getBytes(StandardCharsets.UTF_8);
        byte[] alterados = "mensagem alterada".getBytes(StandardCharsets.UTF_8);

        // 1. Assinatura
        KeyPair ana = Chaves.gerarParAssinatura();
        KeyPair bruno = Chaves.gerarParAssinatura();
        byte[] assinatura = Chaves.assinar(ana.getPrivate(), dados);

        System.out.println("Assinatura da Ana, chave da Ana:  " + Chaves.verificar(ana.getPublic(), dados, assinatura));
        System.out.println("Dados alterados:                  " + Chaves.verificar(ana.getPublic(), alterados, assinatura));
        System.out.println("Chave de outro utilizador:        " + Chaves.verificar(bruno.getPublic(), dados, assinatura));

        // 2. A chave publica viaja como bytes e e reconstruida do outro lado
        byte[] bytesPublica = ana.getPublic().getEncoded();
        PublicKey reconstruida = Chaves.publicaAssinatura(bytesPublica);
        System.out.println("Chave reconstruida a partir de " + bytesPublica.length + " bytes: "
                + Chaves.verificar(reconstruida, dados, assinatura));

        // 3. Acordo de chaves: os dois chegam ao mesmo segredo sem o enviar
        KeyPair a = Chaves.gerarParAcordo();
        KeyPair b = Chaves.gerarParAcordo();
        byte[] segredoA = segredo(a.getPrivate(), b.getPublic());
        byte[] segredoB = segredo(b.getPrivate(), a.getPublic());
        System.out.println("Segredos iguais: " + Arrays.equals(segredoA, segredoB)
                + " (tamanho " + segredoA.length + " bytes)");
    }

    private static byte[] segredo(PrivateKey minhaPrivada, PublicKey publicaDoOutro) throws Exception {
        KeyAgreement ka = KeyAgreement.getInstance("X25519");
        ka.init(minhaPrivada);
        ka.doPhase(publicaDoOutro, true);
        return ka.generateSecret();
    }
}