package com.uklanafood.admin;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class LoginActivity extends AppCompatActivity {
    private EditText phone, pin;
    private Button login;
    private ProgressBar progress;
    private TextView message;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (SessionManager.loggedIn(this)) { openApp(); return; }
        setContentView(R.layout.activity_login);
        phone=findViewById(R.id.loginPhone); pin=findViewById(R.id.loginPin); login=findViewById(R.id.loginButton);
        progress=findViewById(R.id.loginProgress); message=findViewById(R.id.loginMessage);
        login.setOnClickListener(v -> doLogin());
    }

    private void doLogin() {
        String p=phone.getText().toString().replaceAll("\\D+","");
        String code=pin.getText().toString().trim();
        if(p.length()!=10 || code.length()<4){ message.setText("10 digit mobile number aur kam se kam 4 digit PIN bharen."); return; }
        login.setEnabled(false); progress.setVisibility(View.VISIBLE); message.setText("Login check ho raha hai…");
        new Thread(() -> {
            try {
                String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
                if (deviceId == null || deviceId.trim().isEmpty()) throw new Exception("Device ID nahi mili");
                JSONObject body=new JSONObject(); body.put("phone",p); body.put("pin",code); body.put("device_id", deviceId);
                HttpURLConnection c=(HttpURLConnection)new URL(AppConfig.LOGIN_URL).openConnection();
                c.setRequestMethod("POST"); c.setConnectTimeout(15000); c.setReadTimeout(15000); c.setDoOutput(true);
                c.setRequestProperty("Content-Type","application/json; charset=UTF-8"); c.setRequestProperty("Accept","application/json");
                c.setRequestProperty("X-UKF-Device-ID", deviceId);
                try(OutputStream out=c.getOutputStream()){out.write(body.toString().getBytes(StandardCharsets.UTF_8));}
                int status=c.getResponseCode(); JSONObject root=new JSONObject(read(status>=200&&status<300?c.getInputStream():c.getErrorStream()));
                if(!root.optBoolean("ok")) throw new Exception(root.optString("message","Login nahi hua"));
                JSONObject boy=root.optJSONObject("delivery_boy");
                SessionManager.save(this,root.optString("token"),boy==null?"":boy.optString("name"),boy==null?p:boy.optString("phone",p));
                runOnUiThread(this::openApp);
            } catch(Exception e){ runOnUiThread(() -> { login.setEnabled(true); progress.setVisibility(View.GONE); message.setText(e.getMessage()); }); }
        }).start();
    }
    private String read(InputStream in)throws Exception{ if(in==null)return "{}"; StringBuilder s=new StringBuilder(); try(BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){String l;while((l=r.readLine())!=null)s.append(l);}return s.toString(); }
    private void openApp(){ startActivity(new Intent(this,MainActivity.class)); finish(); }
}
