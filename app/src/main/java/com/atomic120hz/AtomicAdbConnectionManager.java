// SPDX-License-Identifier: GPL-3.0-or-later OR Apache-2.0
// ADB key/certificate manager based on the libadb-android sample implementation.

package com.atomic120hz;

import android.content.Context;
import android.os.Build;
import android.sun.misc.BASE64Encoder;
import android.sun.security.provider.X509Factory;
import android.sun.security.x509.AlgorithmId;
import android.sun.security.x509.CertificateAlgorithmId;
import android.sun.security.x509.CertificateExtensions;
import android.sun.security.x509.CertificateIssuerName;
import android.sun.security.x509.CertificateSerialNumber;
import android.sun.security.x509.CertificateSubjectName;
import android.sun.security.x509.CertificateValidity;
import android.sun.security.x509.CertificateVersion;
import android.sun.security.x509.CertificateX509Key;
import android.sun.security.x509.KeyIdentifier;
import android.sun.security.x509.PrivateKeyUsageExtension;
import android.sun.security.x509.SubjectKeyIdentifierExtension;
import android.sun.security.x509.X500Name;
import android.sun.security.x509.X509CertImpl;
import android.sun.security.x509.X509CertInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.spec.EncodedKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Date;
import java.util.Random;

import io.github.muntashirakon.adb.AbsAdbConnectionManager;

public final class AtomicAdbConnectionManager extends AbsAdbConnectionManager {
    private static volatile AtomicAdbConnectionManager instance;
    private final PrivateKey privateKey;
    private final Certificate certificate;

    public static synchronized AtomicAdbConnectionManager getInstance(@NonNull Context context) throws Exception {
        if (instance == null) instance = new AtomicAdbConnectionManager(context.getApplicationContext());
        return instance;
    }

    private AtomicAdbConnectionManager(@NonNull Context context) throws Exception {
        setApi(Build.VERSION.SDK_INT);
        PrivateKey loadedKey = readPrivateKey(context);
        Certificate loadedCertificate = readCertificate(context);

        if (loadedKey == null || loadedCertificate == null) {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048, SecureRandom.getInstance("SHA1PRNG"));
            KeyPair pair = generator.generateKeyPair();
            PublicKey publicKey = pair.getPublic();
            loadedKey = pair.getPrivate();

            String algorithm = "SHA512withRSA";
            Date notBefore = new Date(System.currentTimeMillis() - 60_000L);
            Date notAfter = new Date(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000);
            CertificateExtensions extensions = new CertificateExtensions();
            extensions.set("SubjectKeyIdentifier",
                    new SubjectKeyIdentifierExtension(new KeyIdentifier(publicKey).getIdentifier()));
            extensions.set("PrivateKeyUsage", new PrivateKeyUsageExtension(notBefore, notAfter));

            X500Name name = new X500Name("CN=Atomic 120 Hz");
            X509CertInfo info = new X509CertInfo();
            info.set("version", new CertificateVersion(2));
            info.set("serialNumber", new CertificateSerialNumber(new Random().nextInt() & Integer.MAX_VALUE));
            info.set("algorithmID", new CertificateAlgorithmId(AlgorithmId.get(algorithm)));
            info.set("subject", new CertificateSubjectName(name));
            info.set("key", new CertificateX509Key(publicKey));
            info.set("validity", new CertificateValidity(notBefore, notAfter));
            info.set("issuer", new CertificateIssuerName(name));
            info.set("extensions", extensions);

            X509CertImpl generated = new X509CertImpl(info);
            generated.sign(loadedKey, algorithm);
            loadedCertificate = generated;

            writePrivateKey(context, loadedKey);
            writeCertificate(context, loadedCertificate);
        }
        privateKey = loadedKey;
        certificate = loadedCertificate;
    }

    @NonNull @Override
    protected PrivateKey getPrivateKey() { return privateKey; }

    @NonNull @Override
    protected Certificate getCertificate() { return certificate; }

    @NonNull @Override
    protected String getDeviceName() { return "Atomic 120 Hz"; }

    @Nullable
    private static Certificate readCertificate(Context context) throws IOException, CertificateException {
        File file = new File(context.getFilesDir(), "atomic-adb-cert.pem");
        if (!file.exists()) return null;
        try (InputStream input = new FileInputStream(file)) {
            return CertificateFactory.getInstance("X.509").generateCertificate(input);
        }
    }

    private static void writeCertificate(Context context, Certificate cert)
            throws IOException, CertificateEncodingException {
        File file = new File(context.getFilesDir(), "atomic-adb-cert.pem");
        BASE64Encoder encoder = new BASE64Encoder();
        try (OutputStream output = new FileOutputStream(file)) {
            output.write(X509Factory.BEGIN_CERT.getBytes(StandardCharsets.UTF_8));
            output.write('\n');
            encoder.encode(cert.getEncoded(), output);
            output.write('\n');
            output.write(X509Factory.END_CERT.getBytes(StandardCharsets.UTF_8));
        }
    }

    @Nullable
    private static PrivateKey readPrivateKey(Context context)
            throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        File file = new File(context.getFilesDir(), "atomic-adb-private.key");
        if (!file.exists()) return null;
        byte[] bytes = new byte[(int) file.length()];
        try (InputStream input = new FileInputStream(file)) {
            int offset = 0;
            while (offset < bytes.length) {
                int count = input.read(bytes, offset, bytes.length - offset);
                if (count < 0) throw new IOException("Chave ADB incompleta.");
                offset += count;
            }
        }
        EncodedKeySpec spec = new PKCS8EncodedKeySpec(bytes);
        return KeyFactory.getInstance("RSA").generatePrivate(spec);
    }

    private static void writePrivateKey(Context context, PrivateKey key) throws IOException {
        File file = new File(context.getFilesDir(), "atomic-adb-private.key");
        try (OutputStream output = new FileOutputStream(file)) {
            output.write(key.getEncoded());
        }
    }
}
