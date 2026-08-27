package com.realconnect.app;

import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RecordingsAdapter extends RecyclerView.Adapter<RecordingsAdapter.RecordingViewHolder> {

    public interface OnRecordingDeletedListener {
        void onRecordingDeleted();
    }

    private final Context context;
    private final List<CallRecording> recordings = new ArrayList<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault());
    private final OnRecordingDeletedListener deletedListener;

    private MediaPlayer mediaPlayer;
    private int currentPlayingPosition = -1;
    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private Runnable progressRunnable;

    public RecordingsAdapter(Context context, OnRecordingDeletedListener deletedListener) {
        this.context = context;
        this.deletedListener = deletedListener;
    }

    public void setRecordings(List<CallRecording> list) {
        stopPlayback();
        recordings.clear();
        if (list != null) {
            recordings.addAll(list);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RecordingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_recording, parent, false);
        return new RecordingViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecordingViewHolder holder, int position) {
        CallRecording recording = recordings.get(position);

        String contactName = ContactRepository.getInstance(context).findContactByNumber(recording.getContactPhone());
        String displayName = (contactName != null && !contactName.trim().isEmpty())
                ? contactName
                : (!recording.getContactName().equals("Call") && !recording.getContactName().equals("Unknown") ? recording.getContactName() : recording.getContactPhone());

        holder.textName.setText(displayName);
        holder.textDate.setText(dateFormat.format(new Date(recording.getTimestamp())));

        boolean isPlayingThis = (position == currentPlayingPosition && mediaPlayer != null && mediaPlayer.isPlaying());

        if (isPlayingThis) {
            holder.btnPlayPause.setImageResource(R.drawable.ic_pause);
            holder.layoutProgress.setVisibility(View.VISIBLE);
        } else {
            holder.btnPlayPause.setImageResource(R.drawable.ic_play_arrow);
            if (position == currentPlayingPosition) {
                holder.layoutProgress.setVisibility(View.VISIBLE);
            } else {
                holder.layoutProgress.setVisibility(View.GONE);
            }
        }

        long duration = recording.getDurationMillis();
        holder.textDuration.setText(formatDuration(isPlayingThis && mediaPlayer != null ? mediaPlayer.getCurrentPosition() : 0) + " / " + formatDuration(duration));

        holder.btnPlayPause.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return;

            if (currentPlayingPosition == pos) {
                if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                    mediaPlayer.pause();
                    holder.btnPlayPause.setImageResource(R.drawable.ic_play_arrow);
                } else if (mediaPlayer != null) {
                    mediaPlayer.start();
                    holder.btnPlayPause.setImageResource(R.drawable.ic_pause);
                    startProgressUpdate(holder);
                }
            } else {
                playRecording(pos, holder);
            }
        });

        holder.seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && mediaPlayer != null && currentPlayingPosition == holder.getAdapterPosition()) {
                    mediaPlayer.seekTo(progress);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        holder.btnShare.setOnClickListener(v -> shareRecording(recording));

        holder.btnDelete.setOnClickListener(v -> {
            new AlertDialog.Builder(context)
                    .setTitle("Delete Recording")
                    .setMessage("Are you sure you want to delete this call recording?")
                    .setPositiveButton("Delete", (dialog, which) -> {
                        int pos = holder.getAdapterPosition();
                        if (pos != RecyclerView.NO_POSITION) {
                            if (currentPlayingPosition == pos) {
                                stopPlayback();
                            }
                            CallRecordingHelper.deleteRecording(recording);
                            recordings.remove(pos);
                            notifyItemRemoved(pos);
                            if (deletedListener != null) {
                                deletedListener.onRecordingDeleted();
                            }
                            Toast.makeText(context, "Recording deleted", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    private void playRecording(int position, RecordingViewHolder holder) {
        stopPlayback();
        currentPlayingPosition = position;
        notifyDataSetChanged();

        CallRecording recording = recordings.get(position);
        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(recording.getFilePath());
            mediaPlayer.prepare();
            mediaPlayer.start();

            holder.btnPlayPause.setImageResource(R.drawable.ic_pause);
            holder.layoutProgress.setVisibility(View.VISIBLE);
            holder.seekBar.setMax(mediaPlayer.getDuration());
            holder.seekBar.setProgress(0);

            startProgressUpdate(holder);

            mediaPlayer.setOnCompletionListener(mp -> {
                stopPlayback();
                notifyItemChanged(position);
            });
        } catch (Exception e) {
            Toast.makeText(context, "Error playing audio", Toast.LENGTH_SHORT).show();
            stopPlayback();
        }
    }

    private void startProgressUpdate(RecordingViewHolder holder) {
        stopProgressUpdate();
        progressRunnable = new Runnable() {
            @Override
            public void run() {
                if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                    int currentPos = mediaPlayer.getCurrentPosition();
                    int totalDuration = mediaPlayer.getDuration();
                    holder.seekBar.setProgress(currentPos);
                    holder.textDuration.setText(formatDuration(currentPos) + " / " + formatDuration(totalDuration));
                    progressHandler.postDelayed(this, 200);
                }
            }
        };
        progressHandler.post(progressRunnable);
    }

    private void stopProgressUpdate() {
        if (progressRunnable != null) {
            progressHandler.removeCallbacks(progressRunnable);
            progressRunnable = null;
        }
    }

    public void stopPlayback() {
        stopProgressUpdate();
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
            } catch (Exception ignored) {}
            mediaPlayer = null;
        }
        currentPlayingPosition = -1;
    }

    private void shareRecording(CallRecording recording) {
        try {
            File file = new File(recording.getFilePath());
            if (!file.exists()) {
                Toast.makeText(context, "File not found", Toast.LENGTH_SHORT).show();
                return;
            }

            Uri fileUri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", file);
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("audio/*");
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(Intent.createChooser(shareIntent, "Share Call Recording"));
        } catch (Exception e) {
            Toast.makeText(context, "Unable to share recording", Toast.LENGTH_SHORT).show();
        }
    }

    private String formatDuration(long millis) {
        long seconds = (millis / 1000) % 60;
        long minutes = (millis / (1000 * 60)) % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }

    @Override
    public int getItemCount() {
        return recordings.size();
    }

    static class RecordingViewHolder extends RecyclerView.ViewHolder {
        TextView textName, textDate, textDuration;
        FloatingActionButton btnPlayPause;
        ImageButton btnShare, btnDelete;
        SeekBar seekBar;
        View layoutProgress;

        RecordingViewHolder(@NonNull View itemView) {
            super(itemView);
            textName = itemView.findViewById(R.id.text_recording_name);
            textDate = itemView.findViewById(R.id.text_recording_date);
            textDuration = itemView.findViewById(R.id.text_recording_duration);
            btnPlayPause = itemView.findViewById(R.id.btn_play_pause);
            btnShare = itemView.findViewById(R.id.btn_share_recording);
            btnDelete = itemView.findViewById(R.id.btn_delete_recording);
            seekBar = itemView.findViewById(R.id.seekbar_recording);
            layoutProgress = itemView.findViewById(R.id.layout_player_progress);
        }
    }
}