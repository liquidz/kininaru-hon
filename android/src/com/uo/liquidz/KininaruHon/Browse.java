package com.uo.liquidz.KininaruHon;

import com.uo.liquidz.util.AppengineLogin;
import com.uo.liquidz.util.Callback;

import android.accounts.Account;
import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

public class Browse extends Activity {
	private AppengineLogin login = null;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.browse);

		login = new AppengineLogin(this);
		login.setCallback(new Callback<Account>(){
			public void onSuccess(Account account){
				WebView web = (WebView)findViewById(R.id.web);
				web.setWebViewClient(new WebViewClient(){});
				web.getSettings().setJavaScriptEnabled(true);
				web.loadUrl(getString(R.string.gae_url));
			}
			public void onFail(){
				Toast.makeText(Browse.this, "ログインに失敗しました", Toast.LENGTH_LONG).show();
			}
		});
		login.execute();

    }
}
