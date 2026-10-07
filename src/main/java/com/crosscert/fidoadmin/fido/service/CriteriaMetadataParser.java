package com.crosscert.fidoadmin.fido.service;

import com.crosscert.fidoadmin.fido.entity.Criteria;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * FIDO UAF 메타데이터 문(Metadata Statement) JSON → CRITERIA 행.
 *
 * <p>메타데이터 JSON 을 검증해 CRITERIA 컬럼 값으로 푼다. 숫자 자리에 다른 형이 오면
 * (FIDO2/MDS3 메타데이터처럼 keyProtection 이 문자열 배열인 경우) 저장하지 않고 형식 오류로 돌려보낸다.
 * METAHASH 는 원문 UTF-8 바이트의 SHA-256 을 base64url(패딩 없음)로 둔다 — MDS 관례다.
 * 해시 방식은 FIDO 서버와 맞춰 확인하지 못했다.
 */
@Component
public class CriteriaMetadataParser {

    public static final String INVALID = "유효하지 않은 metadata 형식입니다.";

    /** 첫 JSON 값 뒤에 남은 글자가 있으면 실패시킨다. 끄면 두 문을 붙여 넣었을 때 첫 문만 읽고 원문 전체를 저장한다. */
    private static final ObjectReader READER = new ObjectMapper().reader()
        .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    public Criteria parse(String json) {
        if (json == null || json.isBlank()) throw new CriteriaMetadataException(INVALID);
        JsonNode root;
        try {
            root = READER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new CriteriaMetadataException(INVALID);
        }
        if (root == null || !root.isObject()) throw new CriteriaMetadataException(INVALID);

        JsonNode aaidNode = root.get("aaid");
        if (aaidNode == null || !aaidNode.isTextual() || aaidNode.asText().isBlank()) {
            throw new CriteriaMetadataException(INVALID);
        }
        String aaid = aaidNode.asText().trim();
        int hash = aaid.indexOf('#');

        Criteria c = new Criteria();
        c.setAaid(limit("aaid", aaid, 64));
        c.setVendorids(hash < 0 ? null : limit("vendorId", aaid.substring(0, hash), 32));
        c.setUserverification(userVerification(root.get("userVerificationDetails")));
        c.setKeyprotection(number(root, "keyProtection"));
        c.setMatcherprotection(number(root, "matcherProtection"));
        c.setAttachmenthnumber(number(root, "attachmentHint"));
        c.setTcdisplay(number(root, "tcDisplay"));
        c.setTcdisplaycontenttype(limit("tcDisplayContentType", text(root, "tcDisplayContentType", "text/plain"), 128));
        Long singleAlgorithm = number(root, "authenticationAlgorithm");
        String algorithms = root.has("authenticationAlgorithms")
            ? joinNumbers(root, "authenticationAlgorithms")
            : (singleAlgorithm == null ? null : String.valueOf(singleAlgorithm));
        c.setAuthenticationalgorithms(limit("authenticationAlgorithms", algorithms, 64));
        c.setAssertionschemes(limit("assertionScheme", text(root, "assertionScheme", "UAFV1TLV"), 64));
        c.setAttestationtypes(limit("attestationTypes", joinNumbers(root, "attestationTypes"), 64));
        c.setAuthenticatorversion(number(root, "authenticatorVersion"));
        c.setJsondata(json);
        c.setMetahash(sha256(json));
        return c;
    }

    /** userVerificationDetails 는 [[{userVerification: n}, ...], ...]. 모든 n 을 비트 OR 한다. */
    private static Long userVerification(JsonNode details) {
        if (details == null || details.isNull()) return null;
        if (!details.isArray()) throw invalid("userVerificationDetails");
        long bits = 0;
        boolean any = false;
        for (JsonNode combination : details) {
            if (!combination.isArray()) throw invalid("userVerificationDetails");
            for (JsonNode descriptor : combination) {
                JsonNode uv = descriptor.get("userVerification");
                if (uv == null || !uv.canConvertToExactIntegral()) throw invalid("userVerificationDetails");
                bits |= uv.asLong();
                any = true;
            }
        }
        return any ? bits : null;
    }

    private static Long number(JsonNode root, String field) {
        JsonNode n = root.get(field);
        if (n == null || n.isNull()) return null;
        if (!n.isNumber() || !n.canConvertToExactIntegral()) throw invalid(field);
        return n.asLong();
    }

    private static String text(JsonNode root, String field, String fallback) {
        JsonNode n = root.get(field);
        if (n == null || n.isNull()) return fallback;
        if (!n.isTextual()) throw invalid(field);
        return n.asText();
    }

    private static String joinNumbers(JsonNode root, String field) {
        JsonNode n = root.get(field);
        if (n == null || n.isNull()) return null;
        if (!n.isArray()) throw invalid(field);
        List<String> parts = new ArrayList<>();
        for (JsonNode item : n) {
            if (!item.isNumber() || !item.canConvertToExactIntegral()) throw invalid(field);
            parts.add(String.valueOf(item.asLong()));
        }
        return parts.isEmpty() ? null : String.join(",", parts);
    }

    private static String limit(String field, String value, int maxBytes) {
        if (value != null && value.getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            throw new CriteriaMetadataException(field + " 는 " + maxBytes + "바이트를 넘을 수 없습니다.");
        }
        return value;
    }

    private static CriteriaMetadataException invalid(String field) {
        return new CriteriaMetadataException(INVALID + " (" + field + ")");
    }

    private static String sha256(String json) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // JDK 에 SHA-256 은 항상 있다
        }
    }
}
