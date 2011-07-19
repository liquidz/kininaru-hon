package com.uo.liquidz.util;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.DefaultHttpClient;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

import com.uo.liquidz.KininaruHon.R;

public class AppengineLogin {
	private Context context;
	private String gae_url = null;
	private String authCookie = null;
	private Callback<Account> callback = null;
	private Account account = null;
	private boolean loggedIn = false;

	public AppengineLogin(){}
	public AppengineLogin(Context context){
		this.context = context;
		gae_url = context.getString(R.string.gae_url);
	}

	public void setCallback(Callback<Account> callback){
		this.callback = callback;
	}

	public void execute(){
		if(context != null){
			execute(context);
		}
	}

	public String getAuthCookie(){
		return authCookie;
	}
	public boolean isLoggedIn(){
		return loggedIn;
	}

	public void execute(Context context){
		this.context = context;
		gae_url = context.getString(R.string.gae_url);

		getAuthCookie(new Callback<String>(){
			public void onSuccess(String cookie){
				authCookie = cookie;
				loggedIn = true;
				if(callback != null){
					callback.onSuccess(account);
				}
			}
			public void onFail(){
				if(callback != null){
					callback.onFail();
				}
			}
		});
	}

	protected void getAuthCookie(Callback<String> callback){
		AccountManager accountManager = AccountManager.get(context);
		Account[] accounts = accountManager.getAccountsByType("com.google");
		final Callback<String> cb = callback;

		if(accounts.length > 0){
			this.account = accounts[0];

			SharedPreferences setting = context.getSharedPreferences("Kininaru", 0);

			long last = setting.getLong("lastCachedTime", 0);
			if(last == 0){
				Log.d("kininaru", "calculating yesterday");
				// yesterday
				GregorianCalendar cal = new GregorianCalendar();
				cal.add(Calendar.DATE, -1);
				last = cal.getTime().getTime();
			}
			long now = (new Date()).getTime();

			Log.d("kininaru", "last = " + last);
			Log.d("kininaru", "now  = " + now);

			long diffHours = (now - last) / (1000 * 60 * 60);
			Log.d("kininaru", "diff hour = " + diffHours);
			if(diffHours < 12){
				String cookie = setting.getString("authCookie", null);
				Log.d("kininaru", "using cached cookie: " +  cookie);
				if(cookie != null){
					callback.onSuccess(cookie);
					return;
				}
			}

			accountManager.getAuthToken(accounts[0], "ah", false, new AccountManagerCallback<Bundle>(){
				public void run(AccountManagerFuture<Bundle> result){
					try {
						Bundle bundle = result.getResult();
						Intent intent = (Intent)bundle.get(AccountManager.KEY_INTENT);
						if(intent != null){
							context.startActivity(intent);
						} else {
							Map<String, Object> params = new HashMap<String, Object>();
							params.put("bundle", bundle);
							params.put("callback", cb);
							params.put("context", context);

							new GetCookieTask().execute(params);
						}
					} catch(Exception e){
						e.printStackTrace();
					}
				}
			}, null);
		}
	}

	private class GetCookieTask extends AsyncTask<Map<String, Object>, Integer, String> {
		final int RETRY_MAX = 3;
		private Bundle bundle = null;
		private Context context = null;
		private Callback<String> callback = null;
	
		protected String doInBackground(Map<String, Object>... params) {
			boolean isValidToken = false;
			int retry = 0;
			bundle = (Bundle)params[0].get("bundle");
			callback = (Callback<String>)params[0].get("callback");
			context = (Context)params[0].get("context");
	
			DefaultHttpClient client = null;
			HttpGet httpGet = null;
			HttpResponse response = null;
			//String GAE_APP_URI = "http://test.kininaru-hon.appspot.com";
			String authToken = null;
	
			try {
				while(!isValidToken){
					authToken = bundle.getString(AccountManager.KEY_AUTHTOKEN);
					client = new DefaultHttpClient();
					client.getParams().setBooleanParameter(ClientPNames.HANDLE_REDIRECTS, false);
		
					String uri = gae_url + "/_ah/login?continue="+ gae_url +"/auth&auth=" + authToken;
		
					httpGet = new HttpGet(uri);
					response = client.execute(httpGet);
					int status = response.getStatusLine().getStatusCode();
		
					if(status == HttpStatus.SC_INTERNAL_SERVER_ERROR){
						Log.d("kininaru", "invalid token: " + authToken);
						// remove auth token cache
						String accountType = bundle.getString(AccountManager.KEY_ACCOUNT_TYPE);
						AccountManager manager = AccountManager.get(context);
						manager.invalidateAuthToken(accountType, authToken);
					} else {
						isValidToken = true;
					}
					++retry;
					if(retry > RETRY_MAX){ break; }
				}
			} catch(Exception e){
				e.printStackTrace();
			}

			if(isValidToken){
				for(Cookie cookie : client.getCookieStore().getCookies()){
					if(cookie.getName().equals("SACSID") || cookie.getName().equals("ACSID")){
						return(cookie.getName() + "=" + cookie.getValue());
					}
				}
			}
			return null;
		}
	
		protected void onPostExecute(String cookie){
			if(cookie == null){
				callback.onFail();
			} else {
				callback.onSuccess(cookie);

				Log.d("kininaru", "putting cache data");
				SharedPreferences setting = context.getSharedPreferences("Kininaru", 0);
				SharedPreferences.Editor editor = setting.edit();
				editor.putString("authCookie", cookie);
				editor.putLong("lastCachedTime", (new Date()).getTime());
				editor.commit();
			}
		}
	}
}


