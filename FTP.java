import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

import android.util.Log;

public class FTP {
	public final static String WIN1251 = "windows-1251";

	private final static short bufSize = 1024;

	private final static String TAG = "!->FTP";

	// private final static short LOGIN_SUCCESS = 334;

	public final static String BINARY_FILE_TYPE = "TYPE I";
	public final static String ASCII_FILE_TYPE = "TYPE A";

	private Socket _socket = null;

	private OutputStream _out = null;
	private InputStream _in = null;

	private Socket dataSocket = null;
	private InputStream data = null;
	// private OutputStream dataOut = null;

	public final static byte DEFAULT_PORT = 21;
	public byte mTimeout = 10;

	private String mServer;
	private String mLogin;
	private String mPassword;
	private byte mPort;
	private boolean mIsConnected;

	/*
	 * TODO: Êîíñòðóêòîð
	 */
	public FTP(String server, String login, String passw) {
		mServer = server;
		mLogin = login;
		mPassword = passw;
		mPort = DEFAULT_PORT;
		mIsConnected = false;
	}

	/**
	 * TODO: Êîíñòðóêòîð ñî çíà÷åíèÿìè ïî-óìîë÷àíèþ
	 */
	public FTP() {
		this("ftp.microsoft.com", "anonymous", "ftp.microsoft.com");
	}

	// TODO: send
	private void send(String message) {
		if (!mIsConnected)
			return;

		try {
			_out.write(message.getBytes());
		} catch (Exception e) {
			Log.e(TAG, e.toString());
		}
	}

	// TODO: sendWin1251
	private void sendWin1251(String message) {
		if (!mIsConnected)
			return;

		try {
			_out.write(message.getBytes(WIN1251));
		} catch (Exception e) {
			Log.e(TAG, e.toString());
		}
	}

	private String receive() {
		try {
			byte[] buf = new byte[bufSize];
			_in.read(buf);
			return new String(buf);
		} catch (Exception e) {
			Log.e(TAG, e.toString());
			return "";
		}
	}

	public boolean connect(String server, String login, String password,
			byte port) {
		if (server.length() == 0) {
			Log.e(TAG, "Èìÿ ñåðâåðà íå ìîæåò áûòü ïóñòûì");
			return false;
		}

		mServer = server;
		mLogin = login;
		mPassword = password;
		if (port != 0 && port != DEFAULT_PORT && port > 0) {
			mPort = port;
		}

		try {
			InetSocketAddress sockaddr = new InetSocketAddress(server, port);

			_socket = new Socket();

			_socket.connect(sockaddr, mTimeout * 1000);

			_in = _socket.getInputStream();

			_out = _socket.getOutputStream();

			String unswer = receive();

			// Log.i("!->FTPConnect", unswer);

			if (unswer.contains("220")) {
				Log.i(TAG, "Connect success");
				mIsConnected = true;
			} else {
				Log.i(TAG, "Connect fail");
				mIsConnected = false;
			}
		} catch (Exception e) {
			Log.e(TAG, e.toString() + " to: " + server);
			mIsConnected = false;
		}

		return mIsConnected;
	}

	// TODO: connect()
	public boolean connect() {
		return connect(mServer, mLogin, mPassword, mPort);
	}

	// TODO: login
	public boolean login(String login, String password) {
		if (login.length() == 0) {
			Log.e(TAG, "Èìÿ ïîëüçîâàòåëÿ íå ìîæåò áûòü ïóñòûì");
			return false;
		}

		if (!mIsConnected) {
			Log.e(TAG, "Ñíà÷àëà íóæíî âûïîëíèòü ïîäêëþ÷åíèå");
			return false;
		}

		send(String.format("USER %s\r\n", login));
		String unswer = receive();
		// Log.i("!->USER", unswer);

		send(String.format("PASS %s\r\n", password));
		unswer = receive();
		// Log.i("!->PASS", unswer);

		if (unswer.contains("230")) {
			Log.i(TAG, "Login success");
			return true;
		} else {
			Log.i(TAG, "Login failed");
			return false;
		}
	}

	// TODO: login()
	public boolean login() {
		return login(mLogin, mPassword);
	}

	// TODO: openDataConnection
	private boolean openDataConnection(InetSocketAddress sockaddr) {
		if (!mIsConnected) {
			return false;
		}

		try {
			dataSocket = new Socket();

			dataSocket.connect(sockaddr, mTimeout * 1000);

			data = dataSocket.getInputStream();
		} catch (Exception e) {
			Log.e(TAG, e.toString());
			return false;
		}

		Log.i(TAG, "Data connection successfull");
		return true;
	}

	// TODO: closeDataConnection
	private void closeDataConnection() {
		if (!mIsConnected)
			return;

		try {
			if (data != null)
				data.close();
			if (dataSocket != null)
				dataSocket.close();
		} catch (Exception e) {
			Log.e(TAG, e.toString());
		}
	}

	// TODO: parseDataConnectionString
	private InetSocketAddress parseDataConnectionString(String serverString) {
		InetSocketAddress result = null;
		// String server = "";
		int port = 0;

		int posFirst = serverString.indexOf("(");
		int posLast = serverString.indexOf(")");

		String addr = serverString.substring(posFirst + 1, posLast);

		String[] bytes = addr.split(",");

		try {
			port = Integer.parseInt(bytes[4]) * 256
					+ Integer.parseInt(bytes[5]);
		} catch (Exception e) {
			Log.e(TAG, e.toString());
		}

		result = new InetSocketAddress(mServer, port);
		return result;
	}

	// TODO: enterLocalPassiveMode
	private boolean enterLocalPassiveMode() {
		send(String.format("PASV\r\n"));
		String unswer = receive();

		if (unswer.contains("227")) {
			if (!openDataConnection(parseDataConnectionString(unswer))) {
				Log.e(TAG, "Data connection fail");
				return false;
			}
			return true;
		}
		return false;
	}

	// TODO: setFileTransferMode
	public void setFileTransferMode(String mode) {
		if (!mIsConnected) {
			Log.e(TAG, "Change FileTransferMode fail. Connect before");
		}

		send(String.format("TYPE %s\r\n", mode));
		receive();// String unswer = receive();
		Log.i(TAG, "FileTransferMode successfull changed");
	}

	// TODO: changeWorkingDirectory
	@SuppressWarnings("deprecation")
	public boolean changeWorkingDirectory(String dirName) {
		if (!mIsConnected) {
			Log.e(TAG, "ChangeWorkingDirectory fail. Connect before");
			return false;
		}

		sendWin1251(String.format("CWD %s\r\n", dirName));
		String unswer = "";
		try {
			unswer = new String(receive().getBytes(WIN1251), 0);
		} catch (Exception e) {

		}
		if (unswer.contains("250")) {
			Log.i(TAG, "Directory changed");
			return true;
		} else {
			Log.i(TAG, unswer);
			return false;
		}
	}

	// TODO: retrieveFile
	public boolean retrieveFile(String name, OutputStream os) {
		if (!mIsConnected)
			return false;

		if (!enterLocalPassiveMode()) {
			closeDataConnection();
			return false;
		}

		sendWin1251(String.format("RETR %s\r\n", name));
		String unswer = receive();

		Log.i(TAG, "Transfer starting");

		if (unswer.contains("550")) {
			Log.e(TAG, "Ôàéë íå ñóùåñòâóåò/r/n" + unswer);
			closeDataConnection();
			return false;
		}

		try {
			byte[] buf = new byte[bufSize];
			int cnt = 0;
			while ((cnt = data.read(buf)) != -1) {
				os.write(buf, 0, cnt);
			}
		} catch (Exception e) {
			Log.e(TAG, e.toString());
			closeDataConnection();
			return false;
		}

		unswer = receive();

		if (!unswer.contains("226")) {
			Log.e(TAG, unswer);
			closeDataConnection();
			return false;
		}

		closeDataConnection();
		Log.i(TAG, "Transfer complete");
		return true;
	}

	// TODO: logout
	private void logout() {
		if (!mIsConnected)
			return;

		send(String.format("QUIT\r\n"));
		String unswer = receive();
		if (unswer.contains("221")) {
			Log.i(TAG, "Session closed");
		} else {
			Log.e(TAG, unswer);
		}
	}

	// TODO: disconnect
	public void disconnect() {
		if (!mIsConnected)
			return;

		mIsConnected = false;

		logout();

		try {
			if (_in != null)
				_in.close();
			if (_out != null)
				_out.close();

			if (_socket != null)
				_socket.close();
		} catch (Exception e) {
			Log.e(TAG, e.toString());
		}
	}

	/**
	 * Âîçâðàùàåò ïðèçíàê ïîäêëþ÷åíèÿ
	 * 
	 * @return true åñëè ïîäêëþ÷åí, false - èíà÷å
	 */
	public boolean isConnected() {
		return mIsConnected;
	}
}
