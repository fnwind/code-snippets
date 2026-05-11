package fn.demo.experiments.encrypt;

import cn.hutool.crypto.asymmetric.KeyType;
import cn.hutool.crypto.asymmetric.SM2;

public class Const SM2 {
    public static void main(String[] args) {
        // 生成密钥
        // SM2 sm2 = new SM2();
        // System.out.println("publicKey: " + sm2.getPublicKeyBase64());
        // System.out.println("privateKey: " + sm2.getPrivateKeyBase64());

        // 使用密钥初始化
        String publicKey = "MFkwEwYHKoZIzj0CAQYIKoEcz1UBgi0DQgAEx/ZMz9GCgVNL8fkdK2w3e6syFHf5re8/eCXX31rpjQqqo8/pOJ/nlywxwCOGCjl1iL8ZG9eA0Rs+bOpNyL+aOg==";
        String privateKey = "MIGTAgEAMBMGByqGSM49AgEGCCqBHM9VAYItBHkwdwIBAQQg5+BHngK3sKy86GmtEdhgsy2BSAkRnlbGwnzTOdmF/vagCgYIKoEcz1UBgi2hRANCAATH9kzP0YKBU0vx+R0rbDd7qzIUd/mt7z94JdffWumNCqqjz+k4n+eXLDHAI4YKOXWIvxkb14DRGz5s6k3Iv5o6";
        SM2 sm2 = new SM2(privateKey, publicKey);

        String text = "今天是个好日子";
        String encrypted = sm2.encryptBase64(text, KeyType.PublicKey);
        System.out.println(encrypted);

        String decrypted = sm2.decryptStr(encrypted, KeyType.PrivateKey);
        System.out.println(decrypted);
    }
}

