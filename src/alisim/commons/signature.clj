(ns alisim.commons.signature
  (:import [org.apache.commons.codec.binary Base64 Hex]
           [java.security KeyFactory]
           [java.security.spec X509EncodedKeySpec PKCS8EncodedKeySpec]
           [java.security.cert CertificateFactory]
           [java.security KeyStore KeyStore$PasswordProtection KeyStore$PrivateKeyEntry]
           [javax.crypto Cipher]
           [javax.xml.crypto.dsig XMLSignatureFactory DigestMethod Transform CanonicalizationMethod SignatureMethod XMLSignature]
           [javax.xml.crypto.dsig.dom DOMSignContext DOMValidateContext]
           [javax.xml.crypto.dsig.spec C14NMethodParameterSpec TransformParameterSpec]
           [org.w3c.dom Document Element Node]
           [org.xml.sax InputSource]
           [javax.xml.parsers DocumentBuilder DocumentBuilderFactory]
           [javax.xml.transform TransformerFactory]
           [javax.xml.transform.dom DOMSource]
           [javax.xml.transform.stream StreamResult]
           [javax.xml.xpath XPath XPathConstants XPathExpression XPathExpressionException XPathFactory]
           [java.io File FileInputStream DataInputStream StringWriter ByteArrayInputStream]
           [java.util Collections]))

(defn- get-pem-bytes
  "read contents from pem file."
  [filename key-type]
  (let [pem (-> (slurp filename)
                (.replace (str "-----BEGIN " key-type "-----\n") "")
                (.replace (str "-----END " key-type "-----\n") ""))]
    (println pem)
    (Base64/decodeBase64 pem))
  )

(defn get-pem-private-key
  "read private key from pem file."
  [filename & [opt-alg]]
  (let [key-bytes (get-pem-bytes filename "PRIVATE KEY")
        algorithm (if (nil? opt-alg) "RSA" opt-alg)
        kf (KeyFactory/getInstance algorithm)]
    (.generatePrivate kf (PKCS8EncodedKeySpec. key-bytes))
    )
  )

(defn get-pem-public-key
  "read public key from pem file."
  [filename & [opt-alg]]
  (let [key-bytes (get-pem-bytes filename "PUBLIC KEY")
        algorithm (if (nil? opt-alg) "RSA" opt-alg)
        kf (KeyFactory/getInstance algorithm)]
    (.generatePublic kf (X509EncodedKeySpec. key-bytes))
    )
  )

;; (defn get-pem-certificate
;;   "read certificate from pem file."
;;   [filename]
;;   (let [key-bytes (get-pem-bytes filename "CERTIFICATE")
;;         cf (CertificateFactory/getInstance "X.509")]
;;     (.generateCertificate cf (ByteArrayInputStream. key-bytes))
;;     ))

(defn get-pem-certificate
  "read certificate from pem file."
  [filename]
  (-> (CertificateFactory/getInstance "X.509")
      (.generateCertificate (FileInputStream. filename) ))
  )

(defn get-pkcs12-private-key
  "read private from pfx file."
  [filename & [password]]
  (let [keystore (KeyStore/getInstance "PKCS12")
        pwd-char-array (if (not (nil? password)) (char-array password))
        pwd-char-array (if (not (nil? password)) (.toCharArray password))
        ]
    (.load keystore (FileInputStream. filename) pwd-char-array)
;;    (.getPrivateKey ^KeyStore$PrivateKeyEntry (.getEntry keystore "privateKeyAlias" (KeyStore$PasswordProtection. (.toCharArray password))))
;;    (.getPrivateKey ^KeyStore$PrivateKeyEntry (.getEntry keystore (first (enumeration-seq (.aliases keystore))) (KeyStore$PasswordProtection. (.toCharArray password))))
    (.getKey keystore (first (enumeration-seq (.aliases keystore))) pwd-char-array)
    )
  )

(def key-config (atom {}))

(defn load-key [private-key-file public-key-file & [password]]
  (let [private-key (if (> (->> (slurp private-key-file) (re-seq #"-----BEGIN PRIVATE KEY-----") (count)) 0)
                      (get-pem-private-key private-key-file)
                      (get-pkcs12-private-key private-key-file password))
        public-key (if (> (->> (slurp public-key-file) (re-seq #"-----BEGIN PUBLIC KEY-----") (count)) 0)
                     (get-pem-public-key public-key-file)
                     (-> (get-pem-certificate public-key-file)
                         (.getPublicKey)))]
    (swap! key-config assoc
           :private-key private-key
           :public-key public-key)
    ;; (swap! key-config assoc
    ;;        :private-key (get-pem-private-key private-key-file)
    ;;        ;;         :private-key (get-pkcs12-private-key private-key-file password)
    ;;        ;;         :public-key (get-pem-public-key public-key-file)
    ;;        :public-key (-> (get-pem-certificate public-key-file)
    ;;                        (.getPublicKey))
    ;;        )
    )
  )

(defn generate-xml-signature
  "generate signature for the given xml string"
  [xml-string ref-tag]
  (let [private-key (:private-key @key-config)
        domfac (DocumentBuilderFactory/newInstance)
        _ (.setNamespaceAware domfac true)
        is (-> xml-string
                (.getBytes "utf-8")
                (ByteArrayInputStream.)
                (InputSource.))
        doc (-> (.newDocumentBuilder domfac)
                (.parse is))
        message-node (.. (XPathFactory/newInstance) newXPath (evaluate "Finance/Message" doc XPathConstants/NODE))
        xml-sign-factory (XMLSignatureFactory/getInstance "DOM")
        ;; dom-sign-ctx (DOMSignContext. private-key (-> doc (.getDocumentElement)
        ;;                                               (.getElementsByTagName "Message")
        ;;                                               (.item 0)))
        dom-sign-ctx (DOMSignContext. private-key message-node)
        NIL! nil
        ref (.newReference
             xml-sign-factory
             (str "#" ref-tag)
             (.newDigestMethod xml-sign-factory DigestMethod/SHA1 nil)
             (Collections/singletonList (.newTransform xml-sign-factory Transform/ENVELOPED ^TransformParameterSpec NIL!))
             nil
             nil)
        signed-info (.newSignedInfo
                     xml-sign-factory
                     (.newCanonicalizationMethod xml-sign-factory CanonicalizationMethod/INCLUSIVE ^C14NMethodParameterSpec NIL!)
                     (.newSignatureMethod xml-sign-factory SignatureMethod/RSA_SHA1 nil)
                     (Collections/singletonList ref))
        tf (.. TransformerFactory
               (newInstance)
               (newTransformer))
        writer (StringWriter.)
        sr (StreamResult. writer)]
    (.. xml-sign-factory
        (newXMLSignature signed-info nil)
        (sign dom-sign-ctx))
    (.transform tf (DOMSource. doc) sr)
    (.flush writer)
    (.toString writer))
  )

(defn validate-xml-signature
  "validate signature for the given xml string"
  [xml-string]
  (let [public-key (:public-key @key-config)
        domfac (DocumentBuilderFactory/newInstance)
        _ (.setNamespaceAware domfac true)
        is (-> xml-string
                (.getBytes "utf-8")
                (ByteArrayInputStream.)
                (InputSource.))
        doc (-> (.newDocumentBuilder domfac)
                (.parse is))
        nl (.getElementsByTagNameNS doc XMLSignature/XMLNS "Signature")
        val-ctx (DOMValidateContext. public-key (.item nl 0))
        xml-sign-factory (XMLSignatureFactory/getInstance "DOM")
        signature (.unmarshalXMLSignature xml-sign-factory val-ctx)
        ]
    ;; (if (= (.getLength nl 0) 0)
    ;;   (throw (Exception. "cannot find Signature element")))
    (.validate signature val-ctx))
  

)
