import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Locale;

import android.util.Base64;
import android.util.Log;

public class SMTP {
	private final static short bufSize = 1024;

	private final static String TAG = "!->SMTP";
	private final static String BOUND = "-------------------Boundary";
	// private final static String CHARSET_NAME = "UTF-8";

	private final static short CONNECT_SUCCESS = 220;
	private final static short QUIT_SUCCESS = 221;
	private final static short PASSWORD_SUCCESS = 235;
	private final static short GENERIC_SUCCESS = 250;
	private final static short LOGIN_SUCCESS = 334;
	private final static short DATA_SUCCESS = 354;

	private static Socket _socket = null;

	private static OutputStream _out = null;
	private static InputStream _in = null;

	public static final byte DEFAULT_PORT = 25;
	public static byte mTimeout = 60;

	private static String mServer;
	private static String mLogin;
	private static String mPassword;

	private static int mPort = DEFAULT_PORT;

	private static String mLastError;

	public static String getLastError() {
		return mLastError;
	}

	public static void setServer(String server) {
		mServer = server;
	}

	public static void setLogin(String login) {
		mLogin = login;
	}

	public static void setPassword(String password) {
		mPassword = password;
	}

	public static void setPort(int port) {
		if (0 < port || port < 65535) {
			mPort = port;
		} else {
			mPort = DEFAULT_PORT;
		}
	}

	private static void send(String message) throws IOException {
		send(message.getBytes());
	}

	private static void send(byte[] message) throws IOException {
		_out.write(message);
	}

	private static void send1251(String message) {
		try {
			_out.write(message.getBytes(Const.WIN1251));
		} catch (Exception e) {
			Log.e(TAG, e.getMessage());
		}
	}

	private static void sendAndCodeToBase64(byte[] buffer, int mode)
			throws IOException {
		_out.write(Base64.encode(buffer, mode));
	}

	private static void sendAndCodeToBase64(String message) throws IOException {
		sendAndCodeToBase64(message.getBytes(), Base64.DEFAULT);
	}

	/*
	 * private static void sendAndCodeToBase64_1251(String message) throws
	 * IOException { _out.write(Base64.encode(message.getBytes(App.WIN1251),
	 * Base64.DEFAULT)); }
	 */

	private static String receive() throws IOException {
		byte[] buf = new byte[bufSize];
		_in.read(buf);
		return new String(buf);
	}

	private static boolean connect() throws IOException {
		if (mServer.length() == 0) {
			mLastError = "Èìÿ ñåðâåðà íå ìîæåò áûòü ïóñòûì";
			return false;
		}

		if (mLogin.length() == 0) {
			mLastError = "Èìÿ ïîëüçîâàòåëÿ íå ìîæåò áûòü ïóñòûì";
			return false;
		}

		try {
			InetSocketAddress sockaddr = new InetSocketAddress(mServer, mPort);// _smtpAddress,
																				// _port

			_socket = new Socket();

			_socket.connect(sockaddr, mTimeout * 1000);

			_in = _socket.getInputStream();

			_out = _socket.getOutputStream();
		} catch (Exception e) {
			mLastError = e.getMessage();
			Log.e(TAG, e.getMessage() + mServer);
			return false;
		}

		return true;
	}

	private static void disconnect() throws IOException {
		if (_in != null)
			_in.close();
		if (_out != null)
			_out.close();

		_socket.close();
	}

	public static boolean sendMail(String codeAgent, EMailMessage mailData) throws IOException {
		Log.i(TAG, "Try to connect to " + mServer);
		if (!connect()) {
			Log.e(TAG, "Connect fail");
			disconnect();
			return false;
		}

		if (_out == null || _in == null) {
			Log.e(TAG, "Stream error");
			return false;
		}

		String message = receive();

		if (checkResponse(message) != CONNECT_SUCCESS) {
			mLastError = "Connect fail\r\n" + message;
			disconnect();
			return false;
		}

		message = String.format("HELO %s\r\n", mServer);

		send(message);

		message = receive();

		if (checkResponse(message) != GENERIC_SUCCESS) {
			mLastError = "HELO fail\r\n" + message;
			disconnect();
			return false;
		}

		// Íà÷èíàåì àâòîðèçîâûâàòüñÿ
		send("AUTH LOGIN\r\n");
		message = receive();

		if (checkResponse(message) != LOGIN_SUCCESS) {
			mLastError = "AUTH LOGIN fail\r\n" + message;
			disconnect();
			return false;
		}

		// øëåì ëîãèí
		Log.i(TAG, "Send login");
		sendAndCodeToBase64(mLogin.getBytes(), Base64.CRLF);
		message = receive();
		if (checkResponse(message) != LOGIN_SUCCESS) {
			mLastError = "Íåêîððåêòíîå èìÿ ïîëüçîâàòåëÿ\r\n" + message;
			disconnect();
			return false;
		}

		// øëåì ïàðîëü
		Log.i(TAG, "Send password");
		sendAndCodeToBase64(mPassword.getBytes(), Base64.CRLF);
		message = receive();
		if (checkResponse(message) != PASSWORD_SUCCESS) {
			mLastError = "Íåâåðíûé ïàðîëü\r\n" + message;
			disconnect();
			return false;
		}

		Log.i(TAG, "Authorization complete");

		// Îò êîãî
		message = String.format("MAIL FROM: %s\r\n", mailData.From());
		send(message);
		message = receive();
		if (checkResponse(message) != GENERIC_SUCCESS) {
			mLastError = "MAIL FROM fail\r\n" + message;
			disconnect();
			return false;
		}

		// Êîìó
		message = String.format("RCPT TO: %s\r\n", mailData.To());
		send(message);
		message = receive();
		if (checkResponse(message) != GENERIC_SUCCESS) {
			mLastError = "RCPT TO fail" + message;
			disconnect();
			return false;
		}

		// Äàííûå--------------------------------------------------------------------
		Log.i(TAG, "Send data");
		send("DATA\r\n");
		message = receive();

		if (checkResponse(message) != DATA_SUCCESS) {
			mLastError = "Îøèáêà îòïðàâêè ñåêöèè DATA\r\n" + message;
			disconnect();
			return false;
		}

		// Ôîðìèðóåì çàãîëîâîê
		String tmp = codeAgent;

		SimpleDateFormat sdf = new SimpleDateFormat("ddMMyyyyHHmmss",
				Locale.ENGLISH);

		String mesID = String.format("%s.%s%s", tmp, sdf.format(System
				.currentTimeMillis()),
				mailData.From().substring(mailData.From().lastIndexOf('@')));

		StringBuilder header = new StringBuilder();

		sdf.applyPattern("ddd, dd MMM yyyy HH:mm:ss");

		header.append(String.format("Date: %s +0400\r\n",
				sdf.format(System.currentTimeMillis())));
		header.append(String.format("From: %s\r\n", mailData.From()));
		header.append("X-Priority: 3 (Normal)\r\n");
		header.append(String.format("Message-ID: <%s>\r\n", mesID));
		header.append(String.format("To: %s\r\n", mailData.To()));
		header.append(String.format("Subject: %s\r\n", mailData.Subject()));
		header.append("MIME-Version: 1.0\r\n");

		// Åñëè âëîæåíèé íåò, îòïðàâëÿåì
		Log.i(TAG, "Send message");
		if (mailData.getAttachments().size() == 0) {
			header.append(String.format(
					"Content-Type: text/plain; charset=%s\r\n", Const.UTF8));
			header.append("Content-Transfer-Encoding: base64\r\n\r\n");

			send(header.toString());
			sendAndCodeToBase64(mailData.Body());
		} else// âëîæåíèÿ
		{
			// header = new StringBuilder();
			header.append(String
					.format("Content-Type: multipart/mixed;\r\n boundary=\"%s\"\r\n\r\n",
							BOUND));

			header.append("--" + BOUND + "\r\n");
			header.append("Content-Type: text/plain; charset=Windows-1251\r\n");
			header.append("Content-Transfer-Encoding: base64\r\n\r\n");

			send(header.toString().getBytes(Const.WIN1251));

			sendAndCodeToBase64(mailData.Body().getBytes(Const.WIN1251),
					Base64.DEFAULT);

			for (String item : mailData.getAttachments()) {
				byte last = (byte) item.lastIndexOf(File.separator);
				last += 1;
				String strName = item.substring(last, item.length());

				header = new StringBuilder();
				header.append("--" + BOUND + "\r\n");
				header.append(String.format(
						"Content-Type: TEXT/PLAIN; name=\"%s\"\r\n", strName));
				header.append("Content-transfer-encoding: base64\r\n");
				header.append(String
						.format("Content-Disposition: attachment; filename=\"%s\"\r\n\r\n",
								strName));

				send1251(header.toString());

				// Äàëåå - íåïîñðåäñòâåííî ôàéë
				File att = new File(item);

				if (att.canRead()) {
					FileInputStream fis = new FileInputStream(att);

					int size = att.length() >= Integer.MAX_VALUE ? Integer.MAX_VALUE
							: (int) att.length();
					byte[] buf = new byte[size];
					fis.read(buf);
					sendAndCodeToBase64(buf, Base64.CRLF);
					fis.close();
				}
			}

			send(String.format("--%s--\r\n", BOUND));
		}

		// Çàêàí÷èâàåì
		send("\r\n.\r\n");
		message = receive();
		if (checkResponse(message) != GENERIC_SUCCESS) {
			mLastError = "Îøèáêà îòïðàâêè äàííûõ\r\n" + message;
			disconnect();
			return false;
		}

		// Çàâåðøàåì ñåàíñ
		Log.i(TAG, "Quit");
		send("QUIT\r\n");
		message = receive();
		if (checkResponse(message) != QUIT_SUCCESS) {
			mLastError = "Îøèáêà ïðè çàâåðøåíèè ñåàíñà\r\n" + message;
			disconnect();
			return false;
		}

		disconnect();

		return true;
	}

	private static short checkResponse(String str) {
		short res = 0;

		if (str.length() < 3)
			return res;

		try {
			res = Short.valueOf(str.substring(0, 3));
		} catch (Exception e) {
			mLastError = e.getMessage();
			Log.e(TAG, e.getMessage());
		}

		return res;
	}

}
