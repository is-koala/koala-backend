package com.exskylab.koala.core.utilities.identity;

import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.util.CollectionStore;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Security;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.List;
import java.util.Set;

final class TestChip {

    static final ASN1ObjectIdentifier LDS_SECURITY_OBJECT =
            new ASN1ObjectIdentifier("2.23.136.1.1.1");
    static final ASN1ObjectIdentifier SHA_256 =
            new ASN1ObjectIdentifier("2.16.840.1.101.3.4.2.1");

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    final X509Certificate csca;
    final X509Certificate documentSigner;
    private final KeyPair documentSignerKeys;

    private TestChip(X509Certificate csca, X509Certificate documentSigner, KeyPair documentSignerKeys) {
        this.csca = csca;
        this.documentSigner = documentSigner;
        this.documentSignerKeys = documentSignerKeys;
    }

    static TestChip issue(String countryName) throws Exception {
        KeyPair cscaKeys = keyPair();
        X509Certificate csca = certificate("CN=" + countryName + " CSCA", cscaKeys,
                "CN=" + countryName + " CSCA", cscaKeys, true);

        KeyPair dsKeys = keyPair();
        X509Certificate ds = certificate("CN=" + countryName + " Document Signer", dsKeys,
                "CN=" + countryName + " CSCA", cscaKeys, false);

        return new TestChip(csca, ds, dsKeys);
    }

    Set<TrustAnchor> trustAnchors() {
        return Set.of(new TrustAnchor(csca, null));
    }

    /** TD1: three 30-character lines, the identity number sits in optional data 1. */
    static byte[] dataGroup1(String identityNumber, String yymmdd) {
        return dataGroup1WithUpperLine(pad("I<TUR" + "A01A00000" + "4", 15) + "<" + identityNumber + "<<<", yymmdd);
    }

    /** Upper line verbatim, so a test can build the note (j) truncated-document-number shape too. */
    static byte[] dataGroup1WithUpperLine(String line1, String yymmdd) {
        String line2 = pad(yymmdd + "1" + "M" + "300101" + "7" + "TUR", 29) + "4";
        String line3 = pad("ACMACI<<YUSUF", 30);

        byte[] mrz = (line1 + line2 + line3).getBytes(StandardCharsets.US_ASCII);

        byte[] inner = new byte[3 + mrz.length];
        inner[0] = 0x5F;
        inner[1] = 0x1F;
        inner[2] = (byte) mrz.length;
        System.arraycopy(mrz, 0, inner, 3, mrz.length);

        byte[] outer = new byte[2 + inner.length];
        outer[0] = 0x61;
        outer[1] = (byte) inner.length;
        System.arraycopy(inner, 0, outer, 2, inner.length);

        return outer;
    }

    byte[] securityObject(byte[] dataGroup1Bytes) throws Exception {
        return securityObjectForHash(sha256(dataGroup1Bytes));
    }

    byte[] securityObjectForHash(byte[] dataGroup1Hash) throws Exception {
        ASN1EncodableVector hashEntry = new ASN1EncodableVector();
        hashEntry.add(new ASN1Integer(1));
        hashEntry.add(new DEROctetString(dataGroup1Hash));

        ASN1EncodableVector hashes = new ASN1EncodableVector();
        hashes.add(new DERSequence(hashEntry));

        ASN1EncodableVector lds = new ASN1EncodableVector();
        lds.add(new ASN1Integer(0));
        lds.add(new AlgorithmIdentifier(SHA_256));
        lds.add(new DERSequence(hashes));

        byte[] eContent = new DERSequence(lds).getEncoded("DER");

        CMSSignedDataGenerator generator = new CMSSignedDataGenerator();
        generator.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(
                new JcaDigestCalculatorProviderBuilder().setProvider(BouncyCastleProvider.PROVIDER_NAME).build())
                .build(new JcaContentSignerBuilder("SHA256withRSA")
                        .setProvider(BouncyCastleProvider.PROVIDER_NAME).build(documentSignerKeys.getPrivate()),
                        documentSigner));
        generator.addCertificates(new CollectionStore<>(List.of(
                new org.bouncycastle.cert.X509CertificateHolder(documentSigner.getEncoded()))));

        CMSSignedData signed = generator.generate(
                new org.bouncycastle.cms.CMSProcessableByteArray(LDS_SECURITY_OBJECT, eContent), true);

        return signed.getEncoded();
    }

    static byte[] sha256(byte[] content) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(content);
    }

    private static String pad(String value, int length) {
        StringBuilder sb = new StringBuilder(value);
        while (sb.length() < length) {
            sb.append('<');
        }
        return sb.substring(0, length);
    }

    private static KeyPair keyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static X509Certificate certificate(String subject, KeyPair subjectKeys,
                                               String issuer, KeyPair issuerKeys, boolean ca) throws Exception {
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                new X500Name(issuer), BigInteger.valueOf(System.nanoTime()),
                new Date(System.currentTimeMillis() - 86_400_000L),
                new Date(System.currentTimeMillis() + 365L * 86_400_000L),
                new X500Name(subject), subjectKeys.getPublic());

        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(ca));

        return new JcaX509CertificateConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .getCertificate(builder.build(new JcaContentSignerBuilder("SHA256withRSA")
                        .setProvider(BouncyCastleProvider.PROVIDER_NAME).build(issuerKeys.getPrivate())));
    }
}
