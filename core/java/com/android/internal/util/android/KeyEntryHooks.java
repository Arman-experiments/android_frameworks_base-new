/*
 * Copyright (C) 2024 crDroid Android Project
 *               2024 the risingOS Android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.internal.util.android;

import android.app.ActivityThread;
import android.content.Context;
import android.os.SystemProperties;
import android.security.keystore.KeyProperties;
import android.system.keystore2.KeyEntryResponse;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECParameterSpec;
import java.security.AlgorithmParameters;
import java.util.Base64;
import java.util.concurrent.ThreadLocalRandom;

import com.android.internal.org.bouncycastle.asn1.ASN1Boolean;
import com.android.internal.org.bouncycastle.asn1.ASN1Encodable;
import com.android.internal.org.bouncycastle.asn1.ASN1EncodableVector;
import com.android.internal.org.bouncycastle.asn1.ASN1Enumerated;
import com.android.internal.org.bouncycastle.asn1.ASN1Integer;
import com.android.internal.org.bouncycastle.asn1.ASN1ObjectIdentifier;
import com.android.internal.org.bouncycastle.asn1.ASN1OctetString;
import com.android.internal.org.bouncycastle.asn1.ASN1Primitive;
import com.android.internal.org.bouncycastle.asn1.ASN1Sequence;
import com.android.internal.org.bouncycastle.asn1.ASN1TaggedObject;
import com.android.internal.org.bouncycastle.asn1.DEROctetString;
import com.android.internal.org.bouncycastle.asn1.DERSequence;
import com.android.internal.org.bouncycastle.asn1.DERTaggedObject;
import com.android.internal.org.bouncycastle.asn1.x509.Extension;
import com.android.internal.org.bouncycastle.cert.X509CertificateHolder;
import com.android.internal.org.bouncycastle.cert.X509v3CertificateBuilder;
import com.android.internal.org.bouncycastle.operator.ContentSigner;
import com.android.internal.org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import com.android.internal.R;

/**
 * @hide
 */
public final class KeyEntryHooks {
    private static final String TAG = KeyEntryHooks.class.getSimpleName();
    public static final String ENTRY_HOOKS_ENABLED_PROP = "persist.sys.entryhooks_enabled";
    private static PrivateKey EC, RSA;
    private static byte[] EC_CERTS, RSA_CERTS;
    private static final ASN1ObjectIdentifier OID = new ASN1ObjectIdentifier("1.3.6.1.4.1.11129.2.1.17");
    private static CertificateFactory certificateFactory;
    private static X509CertificateHolder EC_holder, RSA_holder;
    private static volatile String algo;
    private static boolean isInitialized = false;
    
    // Common EC curve OIDs
    private static final String P256_OID = "1.2.840.10045.3.1.7";
    private static final String P384_OID = "1.3.132.0.34";
    private static final String P521_OID = "1.3.132.0.35";
    
    static {
        Log.d(TAG, "KeyEntryHooks static initializer called");
        boolean hooksEnabled = SystemProperties.getBoolean(ENTRY_HOOKS_ENABLED_PROP, false);
        Log.i(TAG, "Entry hooks enabled property: " + hooksEnabled);
        
        if (hooksEnabled) {
            Log.i(TAG, "Entry hooks are ENABLED - beginning initialization");
            try {
                Context context = ActivityThread.currentApplication().getApplicationContext();
                Log.d(TAG, "Application context: " + (context != null ? "available" : "null"));
                
                if (context != null) {
                    try {
                        Log.d(TAG, "Starting certificate factory initialization");
                        certificateFactory = CertificateFactory.getInstance("X.509");
                        Log.d(TAG, "Certificate factory created successfully");
                        
                        Log.d(TAG, "Loading EC private key");
                        EC = parsePrivateKey(context.getResources().getStringArray(R.array.config_key_ec_private), KeyProperties.KEY_ALGORITHM_EC);
                        Log.i(TAG, "EC private key loaded successfully");
                        
                        Log.d(TAG, "Loading RSA private key");
                        RSA = parsePrivateKey(context.getResources().getStringArray(R.array.config_key_rsa_private), KeyProperties.KEY_ALGORITHM_RSA);
                        Log.i(TAG, "RSA private key loaded successfully");
                        
                        Log.d(TAG, "Loading EC certificates");
                        EC_CERTS = loadCertificates(context.getResources().getStringArray(R.array.config_cert_ec));
                        Log.i(TAG, "EC certificates loaded successfully, size: " + (EC_CERTS != null ? EC_CERTS.length : 0) + " bytes");
                        
                        Log.d(TAG, "Loading RSA certificates");
                        RSA_CERTS = loadCertificates(context.getResources().getStringArray(R.array.config_cert_rsa));
                        Log.i(TAG, "RSA certificates loaded successfully, size: " + (RSA_CERTS != null ? RSA_CERTS.length : 0) + " bytes");
                        
                        Log.d(TAG, "Creating EC certificate holder");
                        EC_holder = new X509CertificateHolder(parseCert(context.getResources().getStringArray(R.array.config_cert_ec)[0]));
                        Log.d(TAG, "EC certificate holder created successfully");
                        
                        Log.d(TAG, "Creating RSA certificate holder");
                        RSA_holder = new X509CertificateHolder(parseCert(context.getResources().getStringArray(R.array.config_cert_rsa)[0]));
                        Log.d(TAG, "RSA certificate holder created successfully");
                        
                        isInitialized = true;
                        Log.i(TAG, "KeyEntryHooks initialization completed successfully!");
                        
                    } catch (Throwable t) {
                        Log.e(TAG, "Error during KeyEntryHooks initialization", t);
                        isInitialized = false;
                    }
                } else {
                    Log.w(TAG, "Application context is null - KeyEntryHooks initialization skipped");
                }
            } catch (Exception e) {
                Log.e(TAG, "Exception in KeyEntryHooks static initializer", e);
                isInitialized = false;
            }
        } else {
            Log.i(TAG, "Entry hooks are DISABLED - skipping initialization");
            isInitialized = false;
        }
        
        Log.d(TAG, "KeyEntryHooks static initialization complete. Initialized: " + isInitialized);
    }

    private static PrivateKey parsePrivateKey(String[] keys, String algo) throws Throwable {
        Log.d(TAG, "parsePrivateKey called for algorithm: " + algo);
        
        if (keys == null || keys.length == 0) {
            Log.e(TAG, "Private keys array is null or empty for algorithm: " + algo);
            throw new IllegalArgumentException("Private keys cannot be null or empty");
        }
        
        Log.d(TAG, "Found " + keys.length + " key entries for algorithm: " + algo);
        
        for (int i = 0; i < keys.length; i++) {
            String keyStr = keys[i];
            Log.d(TAG, "Processing key entry " + i + " for algorithm: " + algo);
            
            if (keyStr != null && !keyStr.isEmpty()) {
                try {
                    Log.d(TAG, "Decoding Base64 key for algorithm: " + algo);

                    // Clean up the key string - remove whitespace and fix padding if needed
                    String cleanKey = keyStr.replaceAll("\\s+", "");
                    
                    // Fix Base64 padding if necessary
                    int padding = cleanKey.length() % 4;
                    if (padding != 0) {
                        int paddingNeeded = 4 - padding;
                        cleanKey = cleanKey + "=".repeat(paddingNeeded);
                        Log.d(TAG, "Added " + paddingNeeded + " padding characters to Base64 key");
                    }
                    
                    byte[] bytes = Base64.getDecoder().decode(cleanKey);
                    Log.d(TAG, "Key decoded, length: " + bytes.length + " bytes");
                    
                    // First try PKCS#8 format
                    try {
                        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(bytes);
                        PrivateKey key = KeyFactory.getInstance(algo).generatePrivate(spec);
                        Log.i(TAG, "Successfully parsed private key as PKCS#8 for algorithm: " + algo);
                        return key;
                    } catch (InvalidKeySpecException e1) {
                        Log.d(TAG, "PKCS#8 parsing failed, trying alternative formats for algorithm: " + algo);
                        
                        // For EC keys, try SEC1 format
                        if (KeyProperties.KEY_ALGORITHM_EC.equals(algo)) {
                            try {
                                PrivateKey key = parseECPrivateKeySEC1(bytes);
                                Log.i(TAG, "Successfully parsed EC private key as SEC1 for algorithm: " + algo);
                                return key;
                            } catch (Exception e2) {
                                Log.w(TAG, "SEC1 parsing also failed for EC key entry " + i, e2);
                                
                                // Try raw private key value approach
                                try {
                                    PrivateKey key = parseRawECPrivateKey(bytes);
                                    Log.i(TAG, "Successfully parsed EC private key as raw value for algorithm: " + algo);
                                    return key;
                                } catch (Exception e3) {
                                    Log.w(TAG, "Raw EC key parsing also failed for key entry " + i, e3);
                                }
                            }
                        }
                        
                        // For RSA keys, try alternative Base64 decoding approaches
                        if (KeyProperties.KEY_ALGORITHM_RSA.equals(algo)) {
                            try {
                                PrivateKey key = parseRSAPrivateKeyAlternative(cleanKey);
                                Log.i(TAG, "Successfully parsed RSA private key with alternative method for algorithm: " + algo);
                                return key;
                            } catch (Exception e3) {
                                Log.w(TAG, "Alternative RSA key parsing also failed for key entry " + i, e3);
                            }
                        }
                        // If all parsing methods fail, throw the original exception
                        throw e1;
                    }
                    
                } catch (IllegalArgumentException e) {
                    Log.w(TAG, "Base64 decoding failed for key entry " + i + " for algorithm " + algo, e);
                } catch (Exception e) {
                    Log.w(TAG, "Failed to parse key entry " + i + " for algorithm " + algo, e);
                }
            } else {
                Log.w(TAG, "Key entry " + i + " is null or empty for algorithm: " + algo);
            }
        }
        
        Log.e(TAG, "No valid private keys found for algorithm: " + algo);
        throw new IllegalArgumentException("No valid private keys found");
    }

    private static PrivateKey parseRSAPrivateKeyAlternative(String keyStr) throws Exception {
        Log.d(TAG, "Attempting alternative RSA private key parsing");
        
        // Try URL-safe Base64 decoder
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(keyStr);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(bytes);
            PrivateKey key = KeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_RSA).generatePrivate(spec);
            Log.i(TAG, "Successfully parsed RSA private key with URL-safe Base64 decoder");
            return key;
        } catch (Exception e) {
            Log.d(TAG, "URL-safe Base64 decoding failed, trying MIME decoder", e);
        }
        
        // Try MIME Base64 decoder (handles line breaks and other whitespace)
        try {
            byte[] bytes = Base64.getMimeDecoder().decode(keyStr);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(bytes);
            PrivateKey key = KeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_RSA).generatePrivate(spec);
            Log.i(TAG, "Successfully parsed RSA private key with MIME Base64 decoder");
            return key;
        } catch (Exception e) {
            Log.d(TAG, "MIME Base64 decoding also failed", e);
            throw new Exception("All RSA private key parsing methods failed", e);
        }
    }

    private static PrivateKey parseECPrivateKeySEC1(byte[] keyBytes) throws Exception {
        Log.d(TAG, "Attempting to parse SEC1 EC private key, length: " + keyBytes.length);
        
        try {
            // Parse SEC1 format using BouncyCastle
            ASN1Primitive primitive = ASN1Primitive.fromByteArray(keyBytes);
            
            if (!(primitive instanceof ASN1Sequence)) {
                throw new Exception("Invalid SEC1 key format - not a sequence");
            }
            
            ASN1Sequence sequence = (ASN1Sequence) primitive;
            Log.d(TAG, "SEC1 sequence has " + sequence.size() + " elements");
            
            if (sequence.size() < 2) {
                throw new Exception("Invalid SEC1 key format - insufficient elements");
            }
            
            // Check version (should be 1)
            ASN1Integer version = (ASN1Integer) sequence.getObjectAt(0);
            if (version.getValue().intValue() != 1) {
                Log.w(TAG, "Unexpected SEC1 version: " + version.getValue());
            }
            
            // Extract the private key value (should be at index 1)
            ASN1OctetString privateKeyOctets = (ASN1OctetString) sequence.getObjectAt(1);
            byte[] privateKeyValue = privateKeyOctets.getOctets();
            Log.d(TAG, "Extracted private key value, length: " + privateKeyValue.length);
            
            // Try to extract curve parameters if present
            ECParameterSpec ecParams = null;
            
            // Look for curve parameters in tagged objects
            for (int i = 2; i < sequence.size(); i++) {
                ASN1Encodable element = sequence.getObjectAt(i);
                if (element instanceof ASN1TaggedObject) {
                    ASN1TaggedObject tagged = (ASN1TaggedObject) element;
                    if (tagged.getTagNo() == 0) { // Parameters are usually tag 0
                        try {
                            ASN1ObjectIdentifier curveOid = (ASN1ObjectIdentifier) tagged.getObject();
                            ecParams = getECParameterSpecFromOID(curveOid.getId());
                            Log.d(TAG, "Found curve OID: " + curveOid.getId());
                            break;
                        } catch (Exception e) {
                            Log.d(TAG, "Failed to extract curve OID from tagged object", e);
                        }
                    }
                }
            }
            
            // If no curve params found, try common curves
            if (ecParams == null) {
                Log.d(TAG, "No curve parameters found in SEC1, trying common curves");
                ecParams = tryCommonCurves(privateKeyValue);
            }
            
            if (ecParams == null) {
                throw new Exception("Could not determine EC curve parameters");
            }
            
            // Create the private key
            BigInteger privateKeyInt = new BigInteger(1, privateKeyValue);
            ECPrivateKeySpec keySpec = new ECPrivateKeySpec(privateKeyInt, ecParams);
            
            KeyFactory kf = KeyFactory.getInstance("EC");
            PrivateKey privateKey = kf.generatePrivate(keySpec);
            
            Log.i(TAG, "Successfully created EC private key from SEC1 format");
            return privateKey;
            
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse SEC1 EC key", e);
            throw e;
        }
    }
    
    private static PrivateKey parseRawECPrivateKey(byte[] keyBytes) throws Exception {
        Log.d(TAG, "Attempting to parse raw EC private key, length: " + keyBytes.length);
        
        // Try to interpret the bytes directly as a private key value
        ECParameterSpec ecParams = tryCommonCurves(keyBytes);
        if (ecParams == null) {
            throw new Exception("Could not determine EC curve for raw private key");
        }
        
        BigInteger privateKeyInt = new BigInteger(1, keyBytes);
        ECPrivateKeySpec keySpec = new ECPrivateKeySpec(privateKeyInt, ecParams);
        
        KeyFactory kf = KeyFactory.getInstance("EC");
        PrivateKey privateKey = kf.generatePrivate(keySpec);
        
        Log.i(TAG, "Successfully created EC private key from raw bytes");
        return privateKey;
    }
    
    private static ECParameterSpec getECParameterSpecFromOID(String oid) throws Exception {
        String curveName = null;
        
        switch (oid) {
            case P256_OID:
                curveName = "secp256r1";
                break;
            case P384_OID:
                curveName = "secp384r1";
                break;
            case P521_OID:
                curveName = "secp521r1";
                break;
            default:
                throw new Exception("Unsupported curve OID: " + oid);
        }
        
        AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
        params.init(new java.security.spec.ECGenParameterSpec(curveName));
        return params.getParameterSpec(ECParameterSpec.class);
    }
    
    private static ECParameterSpec tryCommonCurves(byte[] privateKeyValue) {
        String[] commonCurves = {"secp256r1", "secp384r1", "secp521r1"};
        
        for (String curveName : commonCurves) {
            try {
                AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
                params.init(new java.security.spec.ECGenParameterSpec(curveName));
                ECParameterSpec ecParams = params.getParameterSpec(ECParameterSpec.class);
                
                // Check if the private key value is valid for this curve
                BigInteger privateKeyInt = new BigInteger(1, privateKeyValue);
                if (privateKeyInt.compareTo(BigInteger.ZERO) > 0 && 
                    privateKeyInt.compareTo(ecParams.getOrder()) < 0) {
                    
                    // Try to create a key spec to validate
                    ECPrivateKeySpec keySpec = new ECPrivateKeySpec(privateKeyInt, ecParams);
                    KeyFactory kf = KeyFactory.getInstance("EC");
                    kf.generatePrivate(keySpec); // This will throw if invalid
                    
                    Log.d(TAG, "Successfully matched curve: " + curveName);
                    return ecParams;
                }
            } catch (Exception e) {
                Log.d(TAG, "Curve " + curveName + " did not match private key", e);
            }
        }
        
        Log.w(TAG, "No common curves matched the private key");
        return null;
    }

    private static byte[] loadCertificates(String[] certs) throws Exception {
        Log.d(TAG, "loadCertificates called");
        
        if (certs == null || certs.length == 0) {
            Log.e(TAG, "Certificates array is null or empty");
            throw new IllegalArgumentException("Certificates cannot be null or empty");
        }
        
        Log.d(TAG, "Found " + certs.length + " certificate entries");
        
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        int validCerts = 0;
        
        for (int i = 0; i < certs.length; i++) {
            String cert = certs[i];
            Log.d(TAG, "Processing certificate entry " + i);
            
            if (cert != null && !cert.isEmpty()) {
                try {
                    byte[] certBytes = parseCert(cert);
                    stream.write(certBytes);
                    validCerts++;
                    Log.d(TAG, "Certificate " + i + " processed successfully, size: " + certBytes.length + " bytes");
                } catch (Exception e) {
                    Log.w(TAG, "Failed to process certificate entry " + i, e);
                }
            } else {
                Log.w(TAG, "Certificate entry " + i + " is null or empty");
            }
        }
        
        Log.i(TAG, "Loaded " + validCerts + " valid certificates, total size: " + stream.size() + " bytes");
        return stream.toByteArray();
    }

    private static byte[] parseCert(String str) {
        Log.d(TAG, "parseCert called");
        
        if (str == null || str.isEmpty()) {
            Log.e(TAG, "Certificate string is null or empty");
            throw new IllegalArgumentException("Certificate cannot be null or empty");
        }
        
        try {
            byte[] decoded = Base64.getDecoder().decode(str);
            Log.d(TAG, "Certificate decoded successfully, size: " + decoded.length + " bytes");
            return decoded;
        } catch (Exception e) {
            Log.e(TAG, "Failed to decode certificate", e);
            throw e;
        }
    }

    private static byte[] getCertificateChain(String algo) throws Throwable {
        Log.d(TAG, "getCertificateChain called for algorithm: " + algo);
        
        if (KeyProperties.KEY_ALGORITHM_EC.equals(algo)) {
            Log.d(TAG, "Returning EC ertificate chain");
            return EC_CERTS;
        } else if (KeyProperties.KEY_ALGORITHM_RSA.equals(algo)) {
            Log.d(TAG, "Returning RSA certificate chain");
            return RSA_CERTS;
        }
        
        Log.e(TAG, "Unknown algorithm requested: " + algo);
        throw new Exception("Unknown algorithm: " + algo);
    }

    private static byte[] modifyLeaf(byte[] bytes) throws Throwable {
        Log.d(TAG, "modifyLeaf called with certificate size: " + bytes.length + " bytes");
        
        try {
            Log.d(TAG, "Generating X509Certificate from bytes");
            X509Certificate leaf = (X509Certificate) certificateFactory.generateCertificate(new ByteArrayInputStream(bytes));
            Log.d(TAG, "X509Certificate created successfully");
            
            Log.d(TAG, "Checking for required extension: " + OID.getId());
            if (leaf.getExtensionValue(OID.getId()) == null) {
                Log.e(TAG, "Missing required extension: " + OID.getId());
                throw new Exception("Missing extension");
            }
            Log.d(TAG, "Required extension found");
            
            Log.d(TAG, "Creating X509CertificateHolder");
            X509CertificateHolder holder = new X509CertificateHolder(leaf.getEncoded());
            Extension ext = holder.getExtension(OID);
            Log.d(TAG, "Extension extracted successfully");
            
            Log.d(TAG, "Parsing ASN1 sequence");
            ASN1Sequence sequence = ASN1Sequence.getInstance(ext.getExtnValue().getOctets());
            ASN1Encodable[] encodables = sequence.toArray();
            Log.d(TAG, "ASN1 sequence parsed, found " + encodables.length + " elements");
            
            ASN1Sequence teeEnforced = (ASN1Sequence) encodables[7];
            ASN1EncodableVector vector = new ASN1EncodableVector();
            ASN1Sequence rootOfTrust = null;
            
            Log.d(TAG, "Processing TEE enforced elements");
            for (ASN1Encodable asn1Encodable : teeEnforced) {
                ASN1TaggedObject taggedObject = (ASN1TaggedObject) asn1Encodable;
                if (taggedObject.getTagNo() == 704) {
                    rootOfTrust = (ASN1Sequence) taggedObject.getObject();
                    Log.d(TAG, "Found root of trust element");
                    continue;
                }
                vector.add(asn1Encodable);
            }
            
            if (rootOfTrust == null) {
                Log.e(TAG, "Root of trust not found in certificate");
                throw new Exception("Missing root of trust");
            }
            
            algo = leaf.getPublicKey().getAlgorithm();
            Log.i(TAG, "Certificate algorithm detected: " + algo);
            
            boolean isEC = KeyProperties.KEY_ALGORITHM_EC.equals(algo);
            X509CertificateHolder cert1 = isEC ? EC_holder : RSA_holder;
            PrivateKey privateKey = isEC ? EC : RSA;
            
            Log.d(TAG, "Using " + (isEC ? "EC" : "RSA") + " key for signing");
            
            Log.d(TAG, "Building new certificate");
            X509v3CertificateBuilder builder = new X509v3CertificateBuilder(cert1.getSubject(),
                    holder.getSerialNumber(), holder.getNotBefore(), holder.getNotAfter(),
                    holder.getSubject(), holder.getSubjectPublicKeyInfo());
            
            Log.d(TAG, "Creating content signer with algorithm: " + leaf.getSigAlgName());
            ContentSigner signer = new JcaContentSignerBuilder(leaf.getSigAlgName()).build(privateKey);
            
            Log.d(TAG, "Generating random verified boot key");
            byte[] verifiedBootKey = new byte[32];
            ThreadLocalRandom.current().nextBytes(verifiedBootKey);
            
            DEROctetString verifiedBootHash = (DEROctetString) rootOfTrust.getObjectAt(3);
            if (verifiedBootHash == null) {
                Log.d(TAG, "Verified boot hash is null, generating random hash");
                byte[] temp = new byte[32];
                ThreadLocalRandom.current().nextBytes(temp);
                verifiedBootHash = new DEROctetString(temp);
            } else {
                Log.d(TAG, "Using existing verified boot hash");
            }
            
            Log.d(TAG, "Building new root of trust");
            ASN1Encodable[] rootOfTrustEnc = {
                    new DEROctetString(verifiedBootKey),
                    ASN1Boolean.TRUE,
                    new ASN1Enumerated(0),
                    new DEROctetString(verifiedBootHash)
            };
            ASN1Sequence rootOfTrustSeq = new DERSequence(rootOfTrustEnc);
            ASN1TaggedObject rootOfTrustTagObj = new DERTaggedObject(704, rootOfTrustSeq);
            vector.add(rootOfTrustTagObj);
            vector.add(ASN1Boolean.TRUE);
            
            Log.d(TAG, "Building and signing modified certificate");
            byte[] modifiedCert = builder.build(signer).getEncoded();
            Log.i(TAG, "Certificate modification completed successfully, new size: " + modifiedCert.length + " bytes");
            
            return modifiedCert;
            
        } catch (Throwable t) {
            Log.e(TAG, "Error in modifyLeaf", t);
            throw t;
        }
    }

    public static KeyEntryResponse onGetKeyEntry(KeyEntryResponse response) {
        Log.d(TAG, "onGetKeyEntry called");
        
        if (response == null) {
            Log.w(TAG, "KeyEntryResponse is null, returning null");
            return null;
        }
        
        boolean hooksEnabled = SystemProperties.getBoolean(ENTRY_HOOKS_ENABLED_PROP, false);
        Log.d(TAG, "Entry hooks enabled: " + hooksEnabled + ", initialized: " + isInitialized);
        
        if (!hooksEnabled) {
            Log.d(TAG, "Entry hooks disabled, returning original response");
            return response;
        }
        
        if (!isInitialized) {
            Log.w(TAG, "KeyEntryHooks not properly initialized, returning original response");
            return response;
        }
        
        if (response.metadata == null) {
            Log.w(TAG, "KeyEntryResponse metadata is null, returning original response");
            return response;
        }
        
        if (response.metadata.certificate == null) {
            Log.w(TAG, "Certificate in metadata is null, returning original response");
            return response;
        }
        
        Log.i(TAG, "KeyEntryHooks TRIGGERED - processing certificate modification");
        Log.d(TAG, "Original certificate size: " + response.metadata.certificate.length + " bytes");
        
        algo = null;
        try {
            Log.d(TAG, "Starting certificate modification process");
            byte[] newLeaf = modifyLeaf(response.metadata.certificate);
            
            Log.d(TAG, "Getting certificate chain for algorithm: " + algo);
            response.metadata.certificateChain = getCertificateChain(algo);
            
            Log.d(TAG, "Replacing original certificate with modified certificate");
            response.metadata.certificate = newLeaf;
            
            Log.i(TAG, "KeyEntryHooks processing completed successfully!");
            Log.d(TAG, "Modified certificate size: " + newLeaf.length + " bytes");
            Log.d(TAG, "Certificate chain size: " + (response.metadata.certificateChain != null ? response.metadata.certificateChain.length : 0) + " bytes");
            
        } catch (Throwable t) {
            Log.e(TAG, "Error processing KeyEntryResponse in onGetKeyEntry", t);
            Log.w(TAG, "Returning original response due to processing error");
        }
        
        Log.d(TAG, "onGetKeyEntry returning response");
        return response;
    }
}
