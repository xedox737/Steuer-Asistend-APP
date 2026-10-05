package com.example.api;

import android.security.keystore.KeyGenParameterSpec;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.*;
import java.security.cert.Certificate;
import java.security.spec.AlgorithmParameterSpec;
import java.util.*;
import javax.crypto.*;

/** Test-only in-memory AndroidKeyStore adapter. AES/GCM encryption itself uses the real JVM cipher. */
public final class TestAndroidKeyStore {
    private static final Map<String, Key> KEYS = new HashMap<>();
    // Production requests the provider by AndroidKeyStore, so register that exact provider name.
    public static void register() {
        KEYS.clear();
        Security.addProvider(new Provider("AndroidKeyStore", 1.0, "Test-only AndroidKeyStore") {{
            put("KeyStore.AndroidKeyStore", Store.class.getName());
            put("KeyGenerator.AES", Generator.class.getName());
        }});
    }
    public static void remove() { Security.removeProvider("AndroidKeyStore"); KEYS.clear(); }
    public static final class Generator extends KeyGeneratorSpi {
        private String alias;
        @Override protected void engineInit(SecureRandom random) { throw new UnsupportedOperationException(); }
        @Override protected void engineInit(int size, SecureRandom random) { throw new UnsupportedOperationException(); }
        @Override protected void engineInit(AlgorithmParameterSpec parameters, SecureRandom random) {
            alias = ((KeyGenParameterSpec) parameters).getKeystoreAlias();
        }
        @Override protected SecretKey engineGenerateKey() {
            try {
                KeyGenerator generator = KeyGenerator.getInstance("AES", "SunJCE");
                generator.init(256);
                SecretKey key = generator.generateKey(); KEYS.put(alias, key); return key;
            } catch (GeneralSecurityException error) { throw new IllegalStateException(error); }
        }
    }
    public static final class Store extends KeyStoreSpi {
        @Override public Key engineGetKey(String alias, char[] password) { return KEYS.get(alias); }
        @Override public Certificate[] engineGetCertificateChain(String alias) { return null; }
        @Override public Certificate engineGetCertificate(String alias) { return null; }
        @Override public Date engineGetCreationDate(String alias) { return new Date(0); }
        @Override public void engineSetKeyEntry(String alias, Key key, char[] password, Certificate[] chain) { KEYS.put(alias, key); }
        @Override public void engineSetKeyEntry(String alias, byte[] key, Certificate[] chain) { throw new UnsupportedOperationException(); }
        @Override public void engineSetCertificateEntry(String alias, Certificate certificate) { throw new UnsupportedOperationException(); }
        @Override public void engineDeleteEntry(String alias) { KEYS.remove(alias); }
        @Override public Enumeration<String> engineAliases() { return Collections.enumeration(KEYS.keySet()); }
        @Override public boolean engineContainsAlias(String alias) { return KEYS.containsKey(alias); }
        @Override public int engineSize() { return KEYS.size(); }
        @Override public boolean engineIsKeyEntry(String alias) { return KEYS.containsKey(alias); }
        @Override public boolean engineIsCertificateEntry(String alias) { return false; }
        @Override public String engineGetCertificateAlias(Certificate certificate) { return null; }
        @Override public void engineStore(OutputStream stream, char[] password) { throw new UnsupportedOperationException(); }
        @Override public void engineLoad(InputStream stream, char[] password) { }
    }
}
