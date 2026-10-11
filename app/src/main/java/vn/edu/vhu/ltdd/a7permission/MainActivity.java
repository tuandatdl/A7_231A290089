package vn.edu.vhu.ltdd.a7permission;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import android.app.PendingIntent;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "A7_231A290089";
    private static final String CHANNEL_ID = "a7_channel";
    private static final int NOTI_ID = 1001;

    private ImageView imgAnh;
    private TextView tvTrangThai;

    // 1. Launcher xin quyền CAMERA (Khai báo ở cấp field để tránh crash khi xoay màn hình)
    private final ActivityResultLauncher<String> xinQuyenCamera = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            duocCap -> {
                Log.d(TAG, "Kết quả xin quyền CAMERA: " + duocCap);
                if (duocCap) {
                    moCamera();
                } else if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                    // Trạng thái 2: Người dùng từ chối lần 1 -> Hiện hộp thoại giải thích
                    hienGiaiThich();
                } else {
                    // Trạng thái 3: Bị chặn vĩnh viễn (từ chối 2 lần) -> Mời vào Cài đặt
                    hienMoCaiDat();
                }
            }
    );

    // 2. Launcher xin quyền POST_NOTIFICATIONS (Android 13+ / API 33+)
    private final ActivityResultLauncher<String> xinQuyenThongBao = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            duocCap -> {
                if (duocCap) {
                    guiThongBao();
                } else {
                    Toast.makeText(this, R.string.noti_denied, Toast.LENGTH_SHORT).show();
                }
            }
    );

    // 3. Launcher mở Camera lấy ảnh xem trước (Bitmap thu nhỏ, không cần FileProvider)
    private final ActivityResultLauncher<Void> chupAnh = registerForActivityResult(
            new ActivityResultContracts.TakePicturePreview(),
            (Bitmap bitmap) -> {
                if (bitmap != null) {
                    imgAnh.setImageBitmap(bitmap);
                    tvTrangThai.setText(getString(R.string.status_photo,
                            bitmap.getWidth(), bitmap.getHeight()));
                } else {
                    Toast.makeText(this, R.string.photo_cancelled, Toast.LENGTH_SHORT).show();
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        imgAnh = findViewById(R.id.imgAnh);
        tvTrangThai = findViewById(R.id.tvTrangThai);
        Button btnChupAnh = findViewById(R.id.btnChupAnh);
        Button btnThongBao = findViewById(R.id.btnThongBao);
        Button btnCaiDat = findViewById(R.id.btnCaiDat);

        // Khởi tạo kênh thông báo cho Android 8+
        taoKenhThongBao();

        btnChupAnh.setOnClickListener(v -> kiemTraRoiChup());
        btnThongBao.setOnClickListener(v -> kiemTraRoiGuiThongBao());
        btnCaiDat.setOnClickListener(v -> moCaiDatUngDung());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Cập nhật lại giao diện khi người dùng bật quyền trong Cài đặt rồi quay lại
        capNhatTrangThai();
    }

    // --- LUỒNG XIN QUYỀN CAMERA (MẪU 3 NHÁNH CHUẨN GOOGLE) ---
    private void kiemTraRoiChup() {
        int trangThai = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        if (trangThai == PackageManager.PERMISSION_GRANTED) {
            // Nhánh 1: Đã có quyền -> Dùng ngay
            moCamera();
        } else if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            // Nhánh 2: Đã từng từ chối -> Giải thích lý do trước khi hỏi lại
            hienGiaiThich();
        } else {
            // Nhánh 3: Lần đầu hoặc đã bị chặn vĩnh viễn -> Kích hoạt launcher hệ thống
            xinQuyenCamera.launch(Manifest.permission.CAMERA);
        }
    }

    private void hienGiaiThich() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.rationale_title)
                .setMessage(R.string.rationale_camera)
                .setPositiveButton(R.string.agree, (d, w) ->
                        xinQuyenCamera.launch(Manifest.permission.CAMERA))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void hienMoCaiDat() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.blocked_title)
                .setMessage(R.string.blocked_camera)
                .setPositiveButton(R.string.open_settings, (d, w) -> moCaiDatUngDung())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void moCamera() {
        chupAnh.launch(null);
    }

    private void moCaiDatUngDung() {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        i.setData(Uri.fromParts("package", getPackageName(), null));
        startActivity(i);
    }

    // --- LUỒNG XIN QUYỀN VÀ GỬI THÔNG BÁO ---
    private void kiemTraRoiGuiThongBao() {
        // Android 12 trở xuống: Quyền thông báo là normal, gửi được ngay
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            guiThongBao();
            return;
        }

        // Android 13+: Kiểm tra quyền POST_NOTIFICATIONS
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            guiThongBao();
        } else {
            xinQuyenThongBao.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    private void taoKenhThongBao() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel kenh = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            kenh.setDescription(getString(R.string.channel_desc));
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(kenh);
            }
        }
    }

    private void guiThongBao() {
        // NC3: Tạo Intent và PendingIntent để mở lại app khi người dùng bấm vào thông báo
        Intent intentMoApp = new Intent(this, MainActivity.class);
        intentMoApp.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intentMoApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(getString(R.string.noti_title))
                .setContentText(getString(R.string.noti_text))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent) // Gắn PendingIntent vào thông báo
                .setAutoCancel(true);            // Tự động biến mất sau khi bấm

        try {
            NotificationManagerCompat.from(this).notify(NOTI_ID, b.build());
        } catch (SecurityException e) {
            Log.w(TAG, "Thiếu quyền gửi thông báo lúc thực thi", e);
        }
    }

    // --- CẬP NHẬT TRẠNG THÁI HIỂN THỊ LÊN MÀN HÌNH ---
    private void capNhatTrangThai() {
        boolean camera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;

        boolean thongBao = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;

        String s = getString(R.string.status_format,
                camera ? getString(R.string.granted) : getString(R.string.denied),
                thongBao ? getString(R.string.granted) : getString(R.string.denied));

        tvTrangThai.setText(s);
        Log.d(TAG, s.replace("\n", " | "));
    }
}