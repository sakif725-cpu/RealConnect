package com.realconnect.app;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class RecordingsActivity extends AppCompatActivity {

    private RecordingsAdapter adapter;
    private View layoutEmpty;
    private TextView textBadgeCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recordings);

        View header = findViewById(R.id.recordings_header);
        ViewCompat.setOnApplyWindowInsetsListener(header, (v, insets) -> {
            Insets statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.displayCutout());
            int topOffset = statusBarInsets.top > 0 ? statusBarInsets.top : (int) (16 * getResources().getDisplayMetrics().density);
            v.setPadding(
                    v.getPaddingLeft(),
                    topOffset + (int) (4 * getResources().getDisplayMetrics().density),
                    v.getPaddingRight(),
                    (int) (12 * getResources().getDisplayMetrics().density)
            );
            return insets;
        });

        ImageButton btnBack = findViewById(R.id.btn_recordings_back);
        btnBack.setOnClickListener(v -> finish());

        layoutEmpty = findViewById(R.id.layout_empty_recordings);
        textBadgeCount = findViewById(R.id.text_recordings_badge_count);
        RecyclerView recyclerView = findViewById(R.id.recycler_recordings);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RecordingsAdapter(this, this::loadRecordings);
        recyclerView.setAdapter(adapter);

        loadRecordings();
    }

    private void loadRecordings() {
        List<CallRecording> list = CallRecordingHelper.getRecordings(this);
        if (list == null || list.isEmpty()) {
            layoutEmpty.setVisibility(View.VISIBLE);
            textBadgeCount.setVisibility(View.GONE);
            adapter.setRecordings(null);
        } else {
            layoutEmpty.setVisibility(View.GONE);
            textBadgeCount.setVisibility(View.VISIBLE);
            textBadgeCount.setText(String.valueOf(list.size()));
            adapter.setRecordings(list);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (adapter != null) {
            adapter.stopPlayback();
        }
    }
}