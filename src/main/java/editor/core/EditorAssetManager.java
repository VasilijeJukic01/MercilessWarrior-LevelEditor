package editor.core;

import editor.logger.LogType;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.security.spec.KeySpec;
import java.util.HashMap;
import java.util.Map;

public class EditorAssetManager {

    private static volatile EditorAssetManager instance;
    private Map<String, byte[]> assetData;
    private static final String SECRET_KEY = "HPLXBALYYYZWNX";

    private EditorAssetManager() {
        loadAndDecryptAssets();
    }

    public static EditorAssetManager getInstance() {
        if (instance == null) {
            synchronized (EditorAssetManager.class) {
                if (instance == null) {
                    instance = new EditorAssetManager();
                }
            }
        }
        return instance;
    }

    @SuppressWarnings("unchecked")
    private void loadAndDecryptAssets() {
        try (InputStream is = EditorAssetManager.class.getResourceAsStream("/editor_assets.epak")) {
            if (is == null) {
                assetData = new HashMap<>();
                return;
            }
            byte[] encryptedData = is.readAllBytes();
            byte[] decryptedData = decrypt(encryptedData);
            if (decryptedData == null) throw new RuntimeException("Failed to decrypt editor assets!");

            try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(decryptedData))) {
                assetData = (Map<String, byte[]>) ois.readObject();
            }
            if (Framework.getInstance() != null && Framework.getInstance().getGui() != null) {
                Framework.getInstance().log("Loaded encrypted editor_assets.epak successfully!", LogType.INFORMATION);
            }
        } catch (Exception e) {
            assetData = new HashMap<>();
            System.err.println("Failed to load editor assets: " + e.getMessage());
        }
    }

    public byte[] getAsset(String path) {
        return assetData.get(path);
    }

    public InputStream getResourceAsStream(String path) {
        byte[] data = getAsset(path);
        if (data != null) {
            return new ByteArrayInputStream(data);
        }
        return EditorAssetManager.class.getResourceAsStream(path);
    }

    private byte[] decrypt(byte[] data) {
        try {
            byte[] salt = new byte[16];
            System.arraycopy(data, 0, salt, 0, salt.length);
            byte[] iv = new byte[16];
            System.arraycopy(data, salt.length, iv, 0, iv.length);
            byte[] encryptedText = new byte[data.length - salt.length - iv.length];
            System.arraycopy(data, salt.length + iv.length, encryptedText, 0, encryptedText.length);

            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            KeySpec spec = new PBEKeySpec(SECRET_KEY.toCharArray(), salt, 65536, 256);
            SecretKey tmp = factory.generateSecret(spec);
            SecretKey secret = new SecretKeySpec(tmp.getEncoded(), "AES");
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, secret, new IvParameterSpec(iv));
            return cipher.doFinal(encryptedText);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}