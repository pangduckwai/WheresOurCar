package org.sea9.android.crypto;

import android.util.Base64;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;

public class CryptoUtils {
	private static final String TAG = "sea9.crypto";

	private static final String DEFAULT_CHARSET = "UTF-8";
	private static final String DEFAULT_PBE_ALGORITHM = "PBEWITHSHA-256AND192BITAES-CBC-BC";
	private static final String DEFAULT_HASH_ALGORITHM = "SHA-256";
	private static final int DEFAULT_ITERATION = 2048;
	private static final int DEFAULT_SALT_LENGTH = 512;

	/**
	 * Compute the hash of the given input.
	 * @param input input.
	 * @return hash value.
	 */
	public static byte[] hash(byte[] input) {
		try {
			MessageDigest digest = MessageDigest.getInstance(DEFAULT_HASH_ALGORITHM);
			digest.reset();
			return digest.digest(input);
		} catch (NoSuchAlgorithmException e) {
			Log.w(TAG, e.getMessage());
			return null;
		}
	}

	/**
	 * @param msg message to be encrypted.
	 * @param passwd password the encryption key is derived from.
	 * @param salt salt used in the encryption
	 * @return the encrypted message.
	 * @throws BadPaddingException if encryption failed.
	 */
	public static char[] encrypt(char[] msg, char[] passwd, byte[] salt) throws BadPaddingException {
		return convert(encode(doCipher(convert(msg)
				, new PBEKeySpec(passwd)
				, new PBEParameterSpec(salt, DEFAULT_ITERATION)
				, true)));
	}

	/**
	 * @param msg message to be decrypted
	 * @param passwd password the encryption key is derived from.
	 * @param salt salt used in the encryption
	 * @return the decrypted message.
	 * @throws BadPaddingException if decryption failed.
	 */
	public static char[] decrypt(char[] msg, char[] passwd, byte[] salt) throws BadPaddingException {
		return convert(doCipher(decode(convert(msg))
				, new PBEKeySpec(passwd)
				, new PBEParameterSpec(salt, DEFAULT_ITERATION)
				, false));
	}

	/**
	 * Perform Base64 encoding of a byte array. Use before the byte array is converted into a char array.
	 * @param inp the input byte array.
	 * @return the Base64-encoded byte array.
	 */
	public static byte[] encode(byte[] inp) {
		return Base64.encode(inp, Base64.NO_WRAP);
	}

	/**
	 * Perform Base64 decode of a byte array. Use after a char array is converted into the byte array.
	 * @param inp the input Base64-encoded byte array.
	 * @return the decoded byte array.
	 */
	public static byte[] decode(byte[] inp) {
		return Base64.decode(inp, Base64.NO_WRAP);
	}

	/**
	 * Convert a char[] to a byte[].
	 * @param input character array to be converted.
	 * @return the converted byte array.
	 */
	public static byte[] convert(char[] input) {
		if (input == null) return null;

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		OutputStreamWriter wrt;
		try {
			wrt = new OutputStreamWriter(out, DEFAULT_CHARSET);
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}

		try {
			wrt.write(input);
			wrt.flush();
			return out.toByteArray();
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			try {
				wrt.close();
			} catch (IOException e) {
				Log.w(TAG, e);
			}
		}
	}

	/**
	 * Convert a byte[] to a char[].
	 * @param input byte array to be converted.
	 * @return the converted character array.
	 */
	public static char[] convert(byte[] input) {
		if (input == null)
			return null;

		Reader rdr;
		try {
			rdr = new InputStreamReader(new ByteArrayInputStream(input), DEFAULT_CHARSET);
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}

		char[] buf = new char[input.length];
		try {
			if (rdr.read(buf) < 0) throw new RuntimeException("End of stream reached");
			return buf;
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			try {
				rdr.close();
			} catch (IOException e) {
				Log.w(TAG, e);
			}
		}
	}

	public static byte[] generateSalt() {
		byte[] rtn = new byte[DEFAULT_SALT_LENGTH];
		(new SecureRandom()).nextBytes(rtn);
		return rtn;
	}

	/*
	 * Encrypt of decrypt a given message in byte array.
	 * @param msg message to be encrypted or decrypted.
	 * @param alias secret key to use in the operation.
	 * @param flag do encryption if true, decryption otherwise.
	 * @return the convert data in a byte array.
	 */
	public static byte[] doCipher(byte[] msg, KeySpec keySpec, AlgorithmParameterSpec paramSpec, boolean flag)
			throws BadPaddingException {
		try {
			SecretKeyFactory factory = SecretKeyFactory.getInstance(DEFAULT_PBE_ALGORITHM);
			SecretKey key = factory.generateSecret(keySpec);
			Cipher cipher = Cipher.getInstance(key.getAlgorithm());
			cipher.init(((flag) ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE), key, paramSpec);
			return cipher.doFinal(msg);
		} catch (NoSuchAlgorithmException |
				 InvalidKeySpecException |
				 NoSuchPaddingException |
				 InvalidKeyException |
				 InvalidAlgorithmParameterException |
				 IllegalBlockSizeException e) {
			throw new RuntimeException(e);
		}
	}
}
