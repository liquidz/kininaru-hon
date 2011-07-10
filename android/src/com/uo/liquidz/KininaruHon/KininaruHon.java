package com.uo.liquidz.KininaruHon;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import android.accounts.Account;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.uo.liquidz.util.AppengineLogin;
import com.uo.liquidz.util.Callback;

public class KininaruHon extends Activity {
	private AppengineLogin login = null;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

		final TextView msg = (TextView)KininaruHon.this.findViewById(R.id.loginText);
		msg.setText("ログイン中...");

		login = new AppengineLogin(this);
		login.setCallback(new Callback<Account>(){
			public void onSuccess(Account account){
				TextView msg = (TextView)KininaruHon.this.findViewById(R.id.loginText);
				msg.setText(account.name);
			}
			public void onFail(){
				Toast.makeText(KininaruHon.this, "ログインに失敗しました", Toast.LENGTH_LONG).show();
				msg.setText("not logged in");
			}
		});
		login.execute();

		((Button)findViewById(R.id.scan)).setOnClickListener(new OnClickListener(){
			public void onClick(View v){
				if(login.isLoggedIn()){
					Intent intent = new Intent("com.google.zxing.client.android.SCAN");
					intent.putExtra("SCAN_MODE", "ONE_D_MODE");
					try {
						startActivityForResult(intent, 0);
					} catch (ActivityNotFoundException e){
						showMessage("error");
					}
				} else {
					showMessage("ログインしていません");
				}
			}
		});
		
		WebView web = (WebView)findViewById(R.id.web);
		web.setWebViewClient(new WebViewClient(){});
		web.getSettings().setJavaScriptEnabled(true);
		web.loadUrl(getString(R.string.gae_url));
    }

	@Override
	public void onResume(){
		super.onResume();

	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent intent){
		if(requestCode == 0){
			if(resultCode == RESULT_OK){
				String isbn = intent.getStringExtra("SCAN_RESULT");

				DefaultHttpClient client = null;
				HttpGet httpGet = null;
				HttpResponse response = null;

				client = new DefaultHttpClient();
				String GAE_APP_URI = KininaruHon.this.getString(R.string.gae_url);
				String uri = GAE_APP_URI + "/kininaru/add?isbn=" + isbn;
				Log.d("kininaru", "post uri = " + uri);
				httpGet = new HttpGet(uri);
				httpGet.setHeader("Cookie", login.getAuthCookie());
				Log.d("kininaru", "post cookie = " + login.getAuthCookie());
						

				try {
					response = client.execute(httpGet);
	
					InputStream in = response.getEntity().getContent();
					BufferedReader br = new BufferedReader(new InputStreamReader(in));
					String line = br.readLine();
					br.close();
	
					Toast.makeText(KininaruHon.this, line, Toast.LENGTH_LONG).show();
				} catch(Exception e){}

				// post
				//postCollection(barcode);
				//Intent i = new Intent(Collepi.this, PostCollection.class);
				//i.putExtra("ISBN", barcode);
				//startActivity(i);
			}
		}
	}

	private void showMessage(String msg){
		Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
	}
}
