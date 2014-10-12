package freeviewer.headorientation;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

public class MenuActivity extends Activity {

	private LiveCardService.LocalBinder mLiveCardService;

	private ServiceConnection mConnection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			if (service instanceof LiveCardService.LocalBinder) {
				mLiveCardService = (LiveCardService.LocalBinder) service;
				Log.d("HEAD", "retrieved live card service");
			}
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			Log.d("HEAD", "disconnects to live card service");
			// Do nothing.
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Log.d("HEAD", "onCreate bind to live card service");
		super.onCreate(savedInstanceState);
		bindService(new Intent(this, LiveCardService.class), mConnection, 0);
	}

	@Override
	public void onAttachedToWindow() {
		super.onAttachedToWindow();
		openOptionsMenu();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.menu, menu);
		Log.d("HEAD", "onCreateOptionMenu");
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle item selection.
		switch (item.getItemId()) {
		case R.id.action_close:
			Log.d("HEAD", "shutdown datasender");
			try {
				mLiveCardService.stopSending();
				stopService(new Intent(this, LiveCardService.class));
			} catch (SecurityException e) {
				Log.d("HEAD", "exception in stopping livecardService", e);
			}
			return true;
		case R.id.action_send:
			Log.d("HEAD", "ready to send");
			mLiveCardService.sendData();
			return true;
		case R.id.action_calibrate:
			Log.d("HEAD", "calibrating...");
			mLiveCardService.calibrate();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onOptionsMenuClosed(Menu menu) {
		Log.d("HEAD", "Menu close!");
		unbindService(mConnection);
		finish();
	}

}
