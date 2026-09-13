package com.exskylab.koala.core.utilities.identity;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.ASN1Set;

import java.util.Optional;

public final class ActiveAuthenticationInfo {

    public static final ASN1ObjectIdentifier PROTOCOL = new ASN1ObjectIdentifier("2.23.136.1.1.5");

    private static final ActiveAuthenticationInfo ABSENT = new ActiveAuthenticationInfo("absent", null);
    private static final ActiveAuthenticationInfo UNREADABLE = new ActiveAuthenticationInfo("unreadable", null);
    private static final ActiveAuthenticationInfo NOT_DECLARED = new ActiveAuthenticationInfo("no-aa-info", null);

    private final String state;
    private final ASN1ObjectIdentifier signatureAlgorithm;

    private ActiveAuthenticationInfo(String state, ASN1ObjectIdentifier signatureAlgorithm) {
        this.state = state;
        this.signatureAlgorithm = signatureAlgorithm;
    }

    public static ActiveAuthenticationInfo from(byte[] dataGroup14) {
        if (dataGroup14 == null || dataGroup14.length == 0) {
            return ABSENT;
        }

        try {
            ASN1Primitive securityInfos =
                    ASN1Primitive.fromByteArray(LdsFiles.unwrap(dataGroup14, DataGroupTags.DG14));
            Iterable<ASN1Encodable> infos = securityInfos instanceof ASN1Set set
                    ? set
                    : ASN1Sequence.getInstance(securityInfos);

            for (ASN1Encodable entry : infos) {
                ASN1Sequence info = ASN1Sequence.getInstance(entry);
                if (info.size() >= 3 && PROTOCOL.equals(ASN1ObjectIdentifier.getInstance(info.getObjectAt(0)))) {
                    return new ActiveAuthenticationInfo("declared",
                            ASN1ObjectIdentifier.getInstance(info.getObjectAt(2)));
                }
            }
            return NOT_DECLARED;
        } catch (Exception e) {
            return UNREADABLE;
        }
    }

    public Optional<ASN1ObjectIdentifier> signatureAlgorithm() {
        return Optional.ofNullable(signatureAlgorithm);
    }

    public String describe() {
        return signatureAlgorithm == null ? state : signatureAlgorithm.getId();
    }
}
