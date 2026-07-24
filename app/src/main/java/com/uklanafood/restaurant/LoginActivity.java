package com.uklanafood.restaurant;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONObject;

public class LoginActivity extends AppCompatActivity {
    private EditText phone,pin; private Button login; private ProgressBar progress; private TextView message;
    @Override protected void onCreate(Bundle b){super.onCreate(b);if(SessionManager.loggedIn(this)){open();return;}setContentView(R.layout.activity_login);phone=findViewById(R.id.loginPhone);pin=findViewById(R.id.loginPin);login=findViewById(R.id.loginButton);progress=findViewById(R.id.loginProgress);message=findViewById(R.id.loginMessage);login.setOnClickListener(v->doLogin());}
    private void doLogin(){String p=phone.getText().toString().replaceAll("\\D+","");String code=pin.getText().toString().trim();if(p.length()!=10||code.length()<4){message.setText("10 digit mobile number aur kam se kam 4 digit PIN bharen.");return;}login.setEnabled(false);progress.setVisibility(View.VISIBLE);message.setText("Login check ho raha hai…");new Thread(()->{try{JSONObject body=new JSONObject();body.put("phone",p);body.put("pin",code);body.put("device_id",ApiClient.deviceId(this));JSONObject root=ApiClient.post(this,AppConfig.LOGIN_URL,body);if(!root.optBoolean("ok"))throw new Exception(root.optString("message","Login nahi hua"));JSONObject r=root.optJSONObject("restaurant");SessionManager.save(this,root.optString("token"),r==null?"Restaurant":r.optString("name","Restaurant"),r==null?p:r.optString("phone",p));runOnUiThread(this::open);}catch(Exception e){runOnUiThread(()->{login.setEnabled(true);progress.setVisibility(View.GONE);message.setText(e.getMessage());});}}).start();}
    private void open(){startActivity(new Intent(this,MainActivity.class));finish();}
}
