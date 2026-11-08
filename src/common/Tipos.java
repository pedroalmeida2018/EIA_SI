package common;

/* Tipos de trama do protocolo (1 byte no inicio de cada trama). */
public class Tipos {
    public static final byte REGISTO = 3;        // cliente -> relay: registo assinado (ver Registo)
    public static final byte REGISTO_OK = 4;     // relay -> cliente: registo aceite (corpo vazio)
    public static final byte PROCURAR = 5;       // cliente -> relay: nome (UTF-8)
    public static final byte PROCURAR_RESP = 6;  // relay -> cliente: registo original, tal como foi assinado
    public static final byte ENVIAR = 7;         // cliente -> relay: destinatario + envelope cifrado
    public static final byte ENVIAR_OK = 8;      // relay -> cliente: envelope entregue
    public static final byte ENTREGA = 9;        // relay -> cliente: envelope cifrado (o relay nao o consegue ler)
    public static final byte ERRO = 127;         // mensagem de erro (UTF-8)
}
