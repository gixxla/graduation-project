package com.example.graduationproject;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.PhoneNumberFormattingTextWatcher;
import android.util.Log;
import android.view.View;
import android.view.MotionEvent;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.graduationproject.databinding.ActivityLoginBinding;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthMissingActivityForRecaptchaException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.AggregateQuery;
import com.google.firebase.firestore.AggregateQuerySnapshot;
import com.google.firebase.firestore.AggregateSource;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class LoginActivity extends AppCompatActivity {
    private static final String TAG = "LoginActivity";
    private ActivityLoginBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private String mVerificationId;
    private PhoneAuthProvider.ForceResendingToken mResendToken;
    private PhoneAuthProvider.OnVerificationStateChangedCallbacks mCallbacks;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        EdgeToEdge.enable(this);
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        binding.pnumberInput.addTextChangedListener(new PhoneNumberFormattingTextWatcher());

        binding.btnContinue.setOnClickListener(view -> {
            if (binding.pnumberInput.getText().length() != 13) {
                binding.warningmsgNumber.setVisibility(View.VISIBLE);
            } else {
                binding.warningmsgNumber.setVisibility(View.INVISIBLE);
                if (binding.vcodeLayout.getVisibility() == View.GONE) {
                    String phoneNumber = "+82" + binding.pnumberInput.getText().toString().substring(1).replace("-", "");
                    checkUser(phoneNumber);
                } else {
                    PhoneAuthCredential credential = PhoneAuthProvider.getCredential(mVerificationId, binding.vcodeInput.getText().toString());
                    signInWithPhoneAuthCredential(credential);
                }
            }
        });
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        View view = getCurrentFocus();
        if (view != null && (ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_MOVE) && view instanceof EditText && !view.getClass().getName().startsWith("android.webkit.")) {
            int scrcoords[] = new int[2];
            view.getLocationOnScreen(scrcoords);
            float x = ev.getRawX() + view.getLeft() - scrcoords[0];
            float y = ev.getRawY() + view.getTop() - scrcoords[1];
            if (x < view.getLeft() || x > view.getRight() || y < view.getTop() || y > view.getBottom())
                ((InputMethodManager)this.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow((this.getWindow().getDecorView().getApplicationWindowToken()), 0);
        }
        return super.dispatchTouchEvent(ev);
    }

    private void signupAlert() {
        AlertDialog.Builder myAlertBuilder = new AlertDialog.Builder(LoginActivity.this);
        myAlertBuilder.setTitle("알림");
        myAlertBuilder.setMessage("등록되어 있지 않은 번호입니다.\n새로 가입하시겠어요?");
        myAlertBuilder.setPositiveButton("네", (dialog, which) -> {
            Intent intent = new Intent(LoginActivity.this, SignupActivity1.class);
            startActivity(intent);
            overridePendingTransition(R.anim.from_right_enter, R.anim.to_left_exit);
        });
        myAlertBuilder.setNegativeButton("아니오", (dialog, which) -> { });
        myAlertBuilder.show();
    }

    private void checkUser(String phoneNumber) {
        Query queryPhoneNumber = db.collection("users").whereEqualTo("phoneNumber", phoneNumber);
        AggregateQuery countQuery = queryPhoneNumber.count();
        countQuery.get(AggregateSource.SERVER).addOnCompleteListener(new OnCompleteListener<AggregateQuerySnapshot>() {
            @Override
            public void onComplete(@NonNull Task<AggregateQuerySnapshot> task) {
                if (task.isSuccessful()) {
                    AggregateQuerySnapshot snapshot = task.getResult();
                    Log.d(TAG, "Count: " + snapshot.getCount());
                    if (snapshot.getCount() > 0) {
                        getVerificationID(phoneNumber);
                    } else {
                        signupAlert();
                    }
                } else {
                    Log.d(TAG, "Exception: " + task.getException());
                }
            }
        });
    }

    private void getVerificationID(String phoneNumber) {
        mCallbacks = new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    @Override
                    public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
                        Log.d(TAG, "onVerificationCompleted:" + credential);
                        signInWithPhoneAuthCredential(credential);
                    }
                    @Override
                    public void onVerificationFailed(@NonNull FirebaseException e) {
                        Log.w(TAG, "onVerificationFailed", e);
                        if (e instanceof FirebaseAuthInvalidCredentialsException) {
                            binding.warningmsgNumber.setVisibility(View.VISIBLE);
                        } else if (e instanceof FirebaseTooManyRequestsException) {
                            Toast.makeText(LoginActivity.this, "Too many requests. Try later", Toast.LENGTH_SHORT).show();
                        } else if (e instanceof FirebaseAuthMissingActivityForRecaptchaException) {
                            Toast.makeText(LoginActivity.this, "reCAPTCHA attempted with null activity", Toast.LENGTH_SHORT).show();
                        }
                    }
                    @Override
                    public void onCodeSent(@NonNull String verificationId, @NonNull PhoneAuthProvider.ForceResendingToken token) {
                        super.onCodeSent(verificationId, token);
                        Log.d(TAG, "onCodeSent:" + verificationId);
                        binding.warningmsgNumber.setVisibility(View.INVISIBLE);
                        mVerificationId = verificationId;
                        mResendToken = token;
                        enableUserManuallyInputCode();
                    }
                };
        mAuth.setLanguageCode("kr");
        PhoneAuthOptions options =
                PhoneAuthOptions.newBuilder(mAuth)
                        .setPhoneNumber(phoneNumber)
                        .setTimeout(60L, TimeUnit.SECONDS)
                        .setActivity(this)
                        .setCallbacks(mCallbacks).build();
        PhoneAuthProvider.verifyPhoneNumber(options);
    }

    private void enableUserManuallyInputCode() {
        binding.vcodeLayout.setVisibility(View.VISIBLE);
        binding.vcodeInput.requestFocus();
    }

    private void signInWithPhoneAuthCredential(PhoneAuthCredential credential) {
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "signInWithCredential:success");
                        FirebaseUser user = task.getResult().getUser();
                        Toast.makeText(LoginActivity.this, "로그인에 성공했어요.", Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                        finish();
                        overridePendingTransition(R.anim.none, R.anim.to_down_exit);
                        startActivity(intent);
                        // Update UI
                    } else {
                        Log.w(TAG, "signInWithCredential:failure.", task.getException());
                        if (task.getException() instanceof FirebaseAuthInvalidCredentialsException) {
                            binding.warningmsgCode.setVisibility(View.VISIBLE);
                        }
                    }
                });
    }
}
