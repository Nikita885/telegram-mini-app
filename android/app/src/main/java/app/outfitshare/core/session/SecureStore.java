package app.outfitshare.core.session;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import androidx.annotation.Nullable;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Small encrypted key-value store: values are AES-GCM encrypted with a key that never leaves the
 * Android Keystore. Used for the refresh/access tokens.
 */
public final class SecureStore {
  private static final String KEYSTORE = "AndroidKeyStore";
  private static final String ALIAS = "outfitshare_tokens";
  private static final int IV_BYTES = 12;

  private final SharedPreferences prefs;

  public SecureStore(Context context) {
    prefs = context.getSharedPreferences("secure_store", Context.MODE_PRIVATE);
  }

  public void put(String key, @Nullable String value) {
    if (value == null) {
      prefs.edit().remove(key).apply();
      return;
    }
    try {
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key());
      byte[] iv = cipher.getIV();
      byte[] data = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
      ByteBuffer buf = ByteBuffer.allocate(iv.length + data.length).put(iv).put(data);
      prefs.edit().putString(key, Base64.encodeToString(buf.array(), Base64.NO_WRAP)).apply();
    } catch (Exception e) {
      prefs.edit().remove(key).apply();
    }
  }

  @Nullable
  public String get(String key) {
    String stored = prefs.getString(key, null);
    if (stored == null) {
      return null;
    }
    try {
      byte[] all = Base64.decode(stored, Base64.NO_WRAP);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, all, 0, IV_BYTES));
      byte[] plain = cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES);
      return new String(plain, StandardCharsets.UTF_8);
    } catch (Exception e) {
      // Key invalidated (e.g. device restored from backup): treat as signed out.
      prefs.edit().remove(key).apply();
      return null;
    }
  }

  public void clear() {
    prefs.edit().clear().apply();
  }

  private static SecretKey key() throws Exception {
    KeyStore ks = KeyStore.getInstance(KEYSTORE);
    ks.load(null);
    if (ks.containsAlias(ALIAS)) {
      return ((KeyStore.SecretKeyEntry) ks.getEntry(ALIAS, null)).getSecretKey();
    }
    KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
    generator.init(
        new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build());
    return generator.generateKey();
  }
}
