package freeviewer.headorientation;

import java.net.*;
import java.io.*;
import java.util.concurrent.TimeUnit;

import android.util.Log;

public class DataSender extends Thread {
	private boolean mShouldRun;
	private static final String HOST = "192.168.1.100";
	private static final int PORT = 5000;

	public DataSender() {
		mShouldRun = true;
	}

	public synchronized void quit() {
		mShouldRun = false;
	}

	@Override
	public void run() {
		Socket socket = null;
		BufferedReader br = null;
		BufferedWriter bw = null;
		try {
			socket = new Socket(HOST, PORT);
			OutputStream os = socket.getOutputStream();
			OutputStreamWriter osw = new OutputStreamWriter(os);
			bw = new BufferedWriter(osw);

			InputStream is = socket.getInputStream();
			InputStreamReader isr = new InputStreamReader(is);
			br = new BufferedReader(isr);
			while (mShouldRun) {
				String msg = String.valueOf(LiveCardService.getAzimuth());
				bw.write(msg, 0, msg.length());
				bw.flush();
				String res = br.readLine();
				Log.d("HEAD", res);
				TimeUnit.MILLISECONDS.sleep(100);
			}
		} catch (InterruptedException e) {
			Log.d("HEAD", "exception", e);
			mShouldRun = false;
		} catch (UnknownHostException e) {
			Log.d("HEAD", "exception", e);
			mShouldRun = false;
		} catch (IOException e) {
			Log.d("HEAD", "exception", e);
		} finally {
			try {
				br.close();
			} catch (IOException e) {
				Log.d("HEAD", "exception", e);
			}
			try {
				bw.close();
			} catch (IOException e) {
				Log.d("DataSender", "exception", e);
			}
			try {
				socket.close();
			} catch (IOException e) {
				Log.d("DataSender", "exception", e);
			}
		}
	}
}
