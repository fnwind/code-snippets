# SM2

SM2 是国密算法中的一种非对称加密方式，Hutool 中提供了 SM2 算法的封装，底层是基于 bouncycastle 实现的。

## 依赖配置

```xml
<dependencies>
    <dependency>
        <groupId>cn.hutool</groupId>
        <artifactId>hutool-all</artifactId>
        <version>5.8.15</version>
    </dependency>
    <!-- https://mvnrepository.com/artifact/org.bouncycastle/bcprov-jdk18on -->
    <dependency>
        <groupId>org.bouncycastle</groupId>
        <artifactId>bcprov-jdk18on</artifactId>
        <version>1.84</version>
        <scope>compile</scope>
    </dependency>
</dependencies>
```

## 生成密钥对

```java
SM2 sm2 = new SM2();
System.out.println("publicKey: " + sm2.getPublicKeyBase64());
System.out.println("privateKey: " + sm2.getPrivateKeyBase64());
```

## 加密和解密

```java
// 使用密钥初始化
String publicKey = "MFkwEwYHKoZIzj0CAQYIKoEcz1UBgi0DQgAEx/ZMz9GCgVNL8fkdK2w3e6syFHf5re8/eCXX31rpjQqqo8/pOJ/nlywxwCOGCjl1iL8ZG9eA0Rs+bOpNyL+aOg==";
String privateKey = "MIGTAgEAMBMGByqGSM49AgEGCCqBHM9VAYItBHkwdwIBAQQg5+BHngK3sKy86GmtEdhgsy2BSAkRnlbGwnzTOdmF/vagCgYIKoEcz1UBgi2hRANCAATH9kzP0YKBU0vx+R0rbDd7qzIUd/mt7z94JdffWumNCqqjz+k4n+eXLDHAI4YKOXWIvxkb14DRGz5s6k3Iv5o6";
SM2 sm2 = new SM2(privateKey, publicKey);

String text = "今天是个好日子";
String encrypted = sm2.encryptBase64(text, KeyType.PublicKey);
System.out.println(encrypted);

String decrypted = sm2.decryptStr(encrypted, KeyType.PrivateKey);
System.out.println(decrypted);
```