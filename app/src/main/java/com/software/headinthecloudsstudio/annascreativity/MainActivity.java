package com.software.headinthecloudsstudio.annascreativity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.coremedia.iso.IsoFile;
import com.coremedia.iso.boxes.Container;
import com.coremedia.iso.boxes.MediaBox;
import com.coremedia.iso.boxes.MediaInformationBox;
import com.coremedia.iso.boxes.MovieBox;
import com.coremedia.iso.boxes.SampleTableBox;
import com.coremedia.iso.boxes.TimeToSampleBox;
import com.coremedia.iso.boxes.TrackBox;
import com.googlecode.mp4parser.BasicContainer;
import com.googlecode.mp4parser.authoring.Movie;
import com.googlecode.mp4parser.authoring.Track;
import com.googlecode.mp4parser.authoring.builder.DefaultMp4Builder;
import com.googlecode.mp4parser.authoring.container.mp4.MovieCreator;
import com.googlecode.mp4parser.authoring.tracks.CroppedTrack;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class MainActivity extends Activity
{

    private Camera mCamera;
    private TextureView mPreview;
    private MediaRecorder mMediaRecorderCaptureVideo;
    private File mOutputFile;

    private boolean isOnGoing = false;

    private boolean isRecording = false;
    private Button recordStopButton;
    private Button resumeButton;
    private Button chooseAudioButton;

    private int intentActivity_ChooseAudioFile = 0;

    private MediaPlayer mMediaPlayerAudioFile;
    private Uri audioFilePath;
    private EditText audioSpeed;

    private TextView countDownText;
    private EditText countDownStart;
    private EditText countDownPause;

    private Context thisContext;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Grab the different Views of the ACtivity
        mPreview = (TextureView) findViewById(R.id.surface_view);
        recordStopButton = (Button) findViewById(R.id.button_capture);
        resumeButton = (Button) findViewById(R.id.button_Resume);
        chooseAudioButton = (Button) findViewById(R.id.button_ChooseAudioFile);

        audioSpeed = (EditText) findViewById(R.id.editText_AudioSpeed);

        countDownText = (TextView) findViewById(R.id.textView_Count);
        countDownStart = (EditText) findViewById(R.id.editText_CountDownStart);
        countDownPause = (EditText) findViewById(R.id.editText_CountDownFinish);

        thisContext = this;
    }

    // Either Start a new recording or stop the current recording, which saves the file
    public void onRecordStopClick(View view)
    {
        if (isRecording == true)
        {
            // stop recording and release camera and the audio device
            try
            {
                // Video Recording
                mMediaRecorderCaptureVideo.stop();

                // Audio Play
                mMediaPlayerAudioFile.stop();
            } catch (RuntimeException e)
            {
                mOutputFile.delete();
            }

            releaseMediaRecorder(); // release the MediaRecorder object
            mCamera.lock();         // take camera access back from MediaRecorder

            // inform the user that recording has stopped
            setCaptureButtonText("Record");
            isRecording = false;
            isOnGoing = false;
            releaseCamera();
            releaseAudioPlayer();

            MergeVideoAndAudio();

            // Setup the buttons and the text boxes
            chooseAudioButton.setEnabled(true);
            countDownStart.setEnabled(true);
            countDownPause.setEnabled(true);
            audioSpeed.setEnabled(true);
            recordStopButton.setEnabled(false);
            resumeButton.setEnabled(false);
        }
        else
        {
            new MediaPrepareTask().execute(null, null, null);
        }
    }

    // Merge the video captured with the audio used
    private void MergeVideoAndAudio()
    {
        try
        {
            // What was the audio playback speed?
            float audioPlaybackSpeed = Float.parseFloat(audioSpeed.getText().toString());

            // Adjust the video based on what audio playback speed was chosen
            AdjustVideoPlaybackSpeed(audioPlaybackSpeed);

            Movie videoOutput = null;
            Movie audioOutput = null;

            try
            {
                // The chosen audio file was given back as an Uri, but I need a file, so create a temp file to use
                videoOutput = MovieCreator.build(mOutputFile.getPath());
                // The chosen audio file is a Uri, not a File, so convert it
                InputStream in = getContentResolver().openInputStream(audioFilePath);
                OutputStream out = new FileOutputStream(new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM) +
                        File.separator + "tempAudio.m4a"));
                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) > 0)
                {
                    out.write(buf, 0, len);
                }
                out.close();
                in.close();

                audioOutput = MovieCreator.build(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM) +
                        File.separator + "tempAudio.m4a");
            } catch (Exception e)
            {
                e.printStackTrace();
            }

            // What is longer? Video or Audio, also, what was the audio playback speed?
            // Video Length
            IsoFile videoFile = new IsoFile(mOutputFile.getPath());
            double videoLengthInSeconds = (double)
                    videoFile.getMovieBox().getMovieHeaderBox().getDuration() /
                    videoFile.getMovieBox().getMovieHeaderBox().getTimescale();
            videoFile.close();

            // Audio Length
            IsoFile audioFile = new IsoFile(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM) +
                    File.separator + "tempAudio.m4a");
            double audioLengthInSeconds = (double)
                    audioFile.getMovieBox().getMovieHeaderBox().getDuration() /
                    audioFile.getMovieBox().getMovieHeaderBox().getTimescale();
            audioFile.close();

            // Grab all the video tracks, but it should just be 1
            List<Track> finalTrack = new ArrayList<>();
            for (Track track : videoOutput.getTracks())
            {
                if (track.getHandler().equals("vide"))
                {
                    finalTrack.add(track);
                }
            }

            // Get all of the audio tracks, hopefully only 1
            for (Track track : audioOutput.getTracks())
            {
                if (track.getHandler().equals("soun"))
                {
                    // The audio track is longer than the video, so crop the audio to match the video length
                    if (audioLengthInSeconds > videoLengthInSeconds)
                    {
                        CroppedTrack croppedSoundTrack = TrimTrack(track, videoLengthInSeconds);

                        finalTrack.add(croppedSoundTrack);
                    }
                    else
                    {
                        // Audio is same or shorter
                        finalTrack.add(track);
                    }
                }
            }

            // Time to make the final movie
            Movie movie = new Movie();
            movie.setTracks(finalTrack);

            Container mp4file = new DefaultMp4Builder().build(movie);

            // Name of the final video
            String fileName = mOutputFile.getName().split("\\.")[0];
            // Remove the altered from the name
            fileName = fileName.replace("_LENGTHADJUSTED", "");
            fileName = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM) +
                    File.separator + fileName + "_FINAL.mp4";

            try
            {
                // Create the FileChannel to write to the file
                FileChannel fc = new FileOutputStream(new File(fileName)).getChannel();
                mp4file.writeContainer(fc);
                fc.close();
            } catch (Exception e)
            {
                //catch exception
            }

            // Delete the temp audio file
            File tempAudioFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM) +
                    File.separator + "tempAudio.m4a");
            tempAudioFile.delete();

            // Delete the old video file
            mOutputFile.delete();

        } catch (Exception ex)
        {
        }
    }

    // Load the video file and increase/decrease the total playback time by the amount the audio was changed
    private void AdjustVideoPlaybackSpeed(float audioPlaybackSpeed)
    {
        // Adjust the video speed
        VideoSpeed(audioPlaybackSpeed);

        // Adjust the length of the video
        VideoLength(audioPlaybackSpeed);
    }

    // Same speed, or slow down, or speed up
    private void VideoSpeed(float audioPlaybackSpeed)
    {
        Movie videoOutput = null;

        try
        {
            // Load the video
            videoOutput = MovieCreator.build(mOutputFile.getPath());
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        // Grab the video track
        List<Track> finalTrack = new ArrayList<>();
        for (Track track : videoOutput.getTracks())
        {
            if (track.getHandler().equals("vide"))
            {
                finalTrack.add(track);
                // Should only be 1
                break;
            }
        }

        // Create a new movie
        Movie movie = new Movie();
        // Add the video track
        movie.setTracks(finalTrack);

        // Get all info about the video
        BasicContainer mp4file = (BasicContainer) new DefaultMp4Builder().build(movie);
        MovieBox box = (MovieBox) mp4file.getBoxes().get(1);//
        TrackBox bt = (TrackBox) box.getBoxes().get(1);
        MediaBox bm = (MediaBox) bt.getBoxes().get(1);
        MediaInformationBox mib = (MediaInformationBox) bm.getBoxes().get(2);
        SampleTableBox stb = (SampleTableBox) mib.getBoxes().get(2);
        TimeToSampleBox ttsb = (TimeToSampleBox) stb.getBoxes().get(1);

        // Adjust playback speed (DOES NOT CHANGE THE VIDEO LENGTH!!!)
        for (TimeToSampleBox.Entry entry : ttsb.getEntries())
        {
            // Current video speed/delta
            long d = entry.getDelta();

            // 0 - 1 - 2 (what the playback speed could be.... NO ERROR CHECKING!!)
            if (audioPlaybackSpeed < 1)
            {
                // Increase the speed of the video
                // Get the percent and the inverse
                float percent = 1.0f - audioPlaybackSpeed;
                int newDelta = (int) ((float) d - ((float) d * percent));
                entry.setDelta(newDelta);
            }
            else if (audioPlaybackSpeed > 1)
            {
                // Decrease the speed of the video
                float percent = audioPlaybackSpeed - 1.0f;
                int newDelta = (int) ((float) d + ((float) d * percent));
                entry.setDelta(newDelta);
            }
        }

        try
        {
            // Make a new file to write the video to - This is the file name created by the recording, just a time-stamp
            String fileName = mOutputFile.getName().split("\\.")[0];
            // New file name, can't overwrite (?) exsiting file
            fileName = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM) +
                    File.separator + fileName + "_SPEEDADJUSTED.mp4";

            // Open the file to write to
            FileChannel fc = new FileOutputStream(new File(fileName)).getChannel();

            // Write the movie to the file
            mp4file.writeContainer(fc);
            fc.close();

            // Delete the old video file
            mOutputFile.delete();

            // Reopen the new different speed video
            mOutputFile = new File(fileName);

        }
        catch (Exception ex)
        {
        }
    }

    // Based on the audio playback speed, the total video length might need to be adjusted
    private void VideoLength(float audioPlaybackSpeed)
    {
        // If playback speed was less than 1, the video is now too long, so trim it
        Movie videoOutput = null;
        try
        {
            // Load the video
            videoOutput = MovieCreator.build(mOutputFile.getPath());
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        // Current length of the video
        double videoLengthInSeconds =0;
        try
        {
            IsoFile videoFile = new IsoFile(mOutputFile.getPath());
            videoLengthInSeconds = (double)
                    videoFile.getMovieBox().getMovieHeaderBox().getDuration() /
                    videoFile.getMovieBox().getMovieHeaderBox().getTimescale();
            videoFile.close();
        }
        catch (Exception ex)
        {

        }

        // GRab the video track(s)
        List<Track> finalTrack = new ArrayList<>();
        for (Track track : videoOutput.getTracks())
        {
            if (track.getHandler().equals("vide"))
            {
                // 0 - 1 - 2, less than 1, the audio was slowed down, so the video was sped up, so now the length has to be altered as well
                if (audioPlaybackSpeed < 1)
                {
                    double percent = 1.0f - audioPlaybackSpeed;
                    videoLengthInSeconds = (int) ((float) videoLengthInSeconds - ((float) videoLengthInSeconds * percent));

                    // TRim it
                    CroppedTrack croppedSoundTrack = TrimTrack(track, videoLengthInSeconds);

                    finalTrack.add(croppedSoundTrack);

                    // Only 1 video track
                    break;
                }
                else
                    finalTrack.add(track);
            }
        }

        try
        {
            // Create a new movie
            Movie movie = new Movie();
            movie.setTracks(finalTrack);
            Container mp4file = new DefaultMp4Builder().build(movie);
            String fileName = mOutputFile.getName().split("\\.")[0];
            // Get rid of the old name
            fileName = fileName.replace("_SPEEDADJUSTED", "");
            fileName = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM) +
                    File.separator + fileName + "_LENGTHADJUSTED.mp4";

            FileChannel fc = new FileOutputStream(new File(fileName)).getChannel();
            mp4file.writeContainer(fc);
            fc.close();

            // Delete the old video file
            mOutputFile.delete();

            // Reopen the new adjusted video file
            mOutputFile = new File(fileName);
        }
        catch (Exception ex)
        {

        }
    }

    // Trim a track to the desired length
    private CroppedTrack TrimTrack(Track track, double totalLength)
    {
        CroppedTrack croppedTrack;
        double startTime1 = 0;
        double endTime1 = totalLength;

        if (track.getSyncSamples() != null && track.getSyncSamples().length > 0)
        {
            startTime1 = correctTimeToSyncSample(track, startTime1, false);
            endTime1 = correctTimeToSyncSample(track, endTime1, true);
        }

        long currentSample = 0;
        double currentTime = 0;
        double lastTime = -1;
        long startSample1 = -1;
        long endSample1 = -1;

        for (int i = 0; i < track.getSampleDurations().length; i++)
        {
            long delta = track.getSampleDurations()[i];


            if (currentTime > lastTime && currentTime <= startTime1)
            {
                // current sample is still before the new starttime
                startSample1 = currentSample;
            }
            if (currentTime > lastTime && currentTime <= endTime1)
            {
                // current sample is after the new start time and still before the new endtime
                endSample1 = currentSample;
            }

            lastTime = currentTime;
            currentTime += (double) delta / (double) track.getTrackMetaData().getTimescale();
            currentSample++;
        }

        // Create the new track
        croppedTrack = new CroppedTrack(track, startSample1, endSample1);

        return croppedTrack;
    }

    // Unsure, stolen from the internet
    private static double correctTimeToSyncSample(Track track, double cutHere, boolean next)
    {
        double[] timeOfSyncSamples = new double[track.getSyncSamples().length];
        long currentSample = 0;
        double currentTime = 0;
        for (int i = 0; i < track.getSampleDurations().length; i++)
        {
            long delta = track.getSampleDurations()[i];

            if (Arrays.binarySearch(track.getSyncSamples(), currentSample + 1) >= 0)
            {
                timeOfSyncSamples[Arrays.binarySearch(track.getSyncSamples(), currentSample + 1)] = currentTime;
            }
            currentTime += (double) delta / (double) track.getTrackMetaData().getTimescale();
            currentSample++;

        }
        double previous = 0;
        for (double timeOfSyncSample : timeOfSyncSamples)
        {
            if (timeOfSyncSample > cutHere)
            {
                if (next)
                {
                    return timeOfSyncSample;
                }
                else
                {
                    return previous;
                }
            }
            previous = timeOfSyncSample;
        }
        return timeOfSyncSamples[timeOfSyncSamples.length - 1];
    }

    private void setCaptureButtonText(String title)
    {
        recordStopButton.setText(title);
    }

    private void releaseMediaRecorder()
    {
        if (mMediaPlayerAudioFile != null)
        {
            mMediaPlayerAudioFile.reset();
            mMediaPlayerAudioFile.release();
            mMediaPlayerAudioFile = null;
        }
    }

    private void releaseAudioPlayer()
    {
        if (mMediaRecorderCaptureVideo != null)
        {
            // clear recorder configuration
            mMediaRecorderCaptureVideo.reset();
            // release the recorder object
            mMediaRecorderCaptureVideo.release();
            mMediaRecorderCaptureVideo = null;
        }
    }

    private void releaseCamera(){
        if (mCamera != null){
            // release the camera for other applications
            mCamera.release();
            mCamera = null;
        }
    }

    private boolean prepareVideoRecorder()
    {
        // Get the front facing camera
        mCamera = CameraHelper.getDefaultFrontFacingCameraInstance();
        // Make it portrait
        mCamera.setDisplayOrientation(90);

        // We need to make sure that our preview and recording video size are supported by the
        // camera. Query camera to find all the sizes and choose the optimal size given the
        // dimensions of our preview surface.
        Camera.Parameters parameters = mCamera.getParameters();
        List<Camera.Size> mSupportedPreviewSizes = parameters.getSupportedPreviewSizes();
        List<Camera.Size> mSupportedVideoSizes = parameters.getSupportedVideoSizes();
        Camera.Size optimalSize = CameraHelper.getOptimalVideoSize(mSupportedVideoSizes,
                mSupportedPreviewSizes, mPreview.getWidth(), mPreview.getHeight());

        // Use the same size for recording profile.
        CamcorderProfile profile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH);
        profile.videoFrameWidth = optimalSize.width;
        profile.videoFrameHeight = optimalSize.height;

        // likewise for the camera object itself.
        parameters.setPreviewSize(profile.videoFrameWidth, profile.videoFrameHeight);
        mCamera.setParameters(parameters);
        try
        {
            // Requires API level 11+, For backward compatibility use {@link setPreviewDisplay}
            // with {@link SurfaceView}
            mCamera.setPreviewTexture(mPreview.getSurfaceTexture());
        }
        catch (IOException e)
        {
            return false;
        }

        mMediaRecorderCaptureVideo = new MediaRecorder();

        // Step 1: Unlock and set camera to MediaRecorder
        mCamera.unlock();
        mMediaRecorderCaptureVideo.setCamera(mCamera);

        // Step 2: Set sources
        mMediaRecorderCaptureVideo.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        mMediaRecorderCaptureVideo.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mMediaRecorderCaptureVideo.setVideoSize(optimalSize.width, optimalSize.height);
        mMediaRecorderCaptureVideo.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mMediaRecorderCaptureVideo.setVideoEncodingBitRate(30000000);
        // Portrait
        mMediaRecorderCaptureVideo.setOrientationHint(270);


        // Step 4: Set output file
        mOutputFile = CameraHelper.getOutputMediaFile();
        if (mOutputFile == null) {
            return false;
        }

        mMediaRecorderCaptureVideo.setOutputFile(mOutputFile.getPath());

        // Step 5: Prepare configured MediaRecorder
        try
        {
            mMediaRecorderCaptureVideo.prepare();
        }
        catch (IllegalStateException e)
        {
            releaseMediaRecorder();
            return false;
        }
        catch (IOException e)
        {
            releaseMediaRecorder();
            return false;
        }
        return true;
    }

    private boolean prepareAudioPlayer()
    {
        boolean result = true;

        mMediaPlayerAudioFile = new MediaPlayer();
        try
        {
            // Get the number from the text box
            float speed = Float.parseFloat(audioSpeed.getText().toString());

            // Set the audio file to play
            mMediaPlayerAudioFile.setDataSource(thisContext, audioFilePath);

            // Play back speed 1 is normal
            mMediaPlayerAudioFile.setPlaybackParams(mMediaPlayerAudioFile.getPlaybackParams().setSpeed(speed));

            // Prepare it!
            mMediaPlayerAudioFile.prepare();
        }
        catch (Exception ex)
        {
            releaseAudioPlayer();
            result = false;
        }

        return result;
    }

    public void onResumeClick(View view)
    {
        // Chose to resume, can't stop
        recordStopButton.setEnabled(false);

        // Can't resume
        resumeButton.setEnabled(false);

        // Can't change the numbers
        countDownStart.setEnabled(false);
        countDownPause.setEnabled(false);
        audioSpeed.setEnabled(false);

        // Get the count timer
        int totalCount = Integer.parseInt(countDownStart.getText().toString());
        countDownText.setText(countDownStart.getText().toString());

        CountDownTimer timerCountDown;
        timerCountDown = new CountDownTimer((totalCount+1) * 1000, 1000)
        {
            boolean firstTick = false;

            public void onTick(long millisUntilFinished)
            {
                // onTick gets called at start, but I don't need it to run till the next tick
                if (firstTick == true)
                {
                    runOnUiThread(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            int currentCount = Integer.parseInt(countDownText.getText().toString());
                            --currentCount;
                            countDownText.setText(String.valueOf(currentCount));
                        }
                    });
                }
                else
                    firstTick = true;
            }

            public void onFinish()
            {
                // Start the timer before pause
                CountDownTimer timerForPause;
                int totalCount = Integer.parseInt(countDownPause.getText().toString());

                runOnUiThread(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        int totalCount = Integer.parseInt(countDownPause.getText().toString());
                        countDownText.setText(String.valueOf(totalCount));
                    }
                });

                timerForPause = new CountDownTimer((totalCount+1) * 1000, 1000)
                {
                    boolean firstTick = false;

                    public void onTick(long millisUntilFinished)
                    {
                        // onTick gets called at start, but I don't need it to run till the next tick
                        if (firstTick == true)
                        {
                            runOnUiThread(new Runnable()
                            {
                                @Override
                                public void run()
                                {
                                    int currentCount = Integer.parseInt(countDownText.getText().toString());
                                    --currentCount;
                                    countDownText.setText(String.valueOf(currentCount));
                                }
                            });
                        }
                        else
                            firstTick = true;
                    }

                    public void onFinish()
                    {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run()
                            {
                                // Pause everything
                                isOnGoing = false;
                                mMediaRecorderCaptureVideo.pause();
                                mMediaPlayerAudioFile.pause();

                                // Allow for stopping
                                recordStopButton.setEnabled(true);

                                // Allow for resume
                                resumeButton.setEnabled(true);

                                // Change the numbers allowed
                                countDownStart.setEnabled(true);
                                countDownPause.setEnabled(true);
                                audioSpeed.setEnabled(true);
                            }
                        });
                    }
                }.start();

                runOnUiThread(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        // This is a resume
                        mMediaRecorderCaptureVideo.resume();
                        mMediaPlayerAudioFile.start();
                    }
                });
            }
        }.start();
    }

    // Ask the user what audio file to open
    public void onChooseAudioFileClick(View view)
    {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("audio/*");      //all files, ONLY M4A FILES SUPPORTED!!
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        try
        {
            startActivityForResult(Intent.createChooser(intent, "Select an Audio File To Play"), intentActivity_ChooseAudioFile);
        }
        catch (android.content.ActivityNotFoundException ex)
        {
            // Potentially direct the user to the Market with a Dialog
            Toast.makeText(this, "Please install a File Manager.", Toast.LENGTH_SHORT).show();
        }
    }

    // Get the Uri for the audio file to open
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        // Chose Audio File
        if (requestCode == intentActivity_ChooseAudioFile)
        {
            // All good
            if (resultCode == RESULT_OK)
            {
                // Get the data
                try
                {
                    audioFilePath = data.getData();

                    // All good, allow for recording
                    recordStopButton.setEnabled(true);
                }
                catch (Exception ex)
                {
                    audioFilePath = null;
                }
            }
        }
    }

    // Start the recording and audio playing
    class MediaPrepareTask extends AsyncTask<Void, Void, Boolean> {

        @Override
        protected Boolean doInBackground(Void... voids)
        {
            // initialize video camera
            if (prepareVideoRecorder() && prepareAudioPlayer())
            {
                return true;
            }
            else
            {
                // prepare didn't work, release the camera
                releaseMediaRecorder();
                releaseAudioPlayer();
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (!result)
            {
                MainActivity.this.finish();
            }
            else
            {
                // Don't allow stop during playback, that's what the timer is for
                recordStopButton.setEnabled(false);

                // Can't change the numbers
                countDownStart.setEnabled(false);
                countDownPause.setEnabled(false);
                audioSpeed.setEnabled(false);

                // Can't choose an audio file
                chooseAudioButton.setEnabled(false);

                // inform the user that recording has started
                setCaptureButtonText("Stop");

                // Get the count down timer
                int totalCount = Integer.parseInt(countDownStart.getText().toString());
                countDownText.setText(countDownStart.getText().toString());

                CountDownTimer timerCountDown;
                timerCountDown = new CountDownTimer((totalCount+1) * 1000, 1000)
                {
                    Boolean firstTick = false;

                    public void onTick(long millisUntilFinished)
                    {
                        // onTick gets called at start, but I don't need it to run till the next tick
                        if (firstTick == true)
                        {
                            runOnUiThread(new Runnable()
                            {
                                @Override
                                public void run()
                                {
                                    int currentCount = Integer.parseInt(countDownText.getText().toString());
                                    --currentCount;
                                    countDownText.setText(String.valueOf(currentCount));
                                }
                            });
                        }
                        else
                            firstTick = true;
                    }

                    public void onFinish()
                    {
                        // Start the timer before pause
                        CountDownTimer timerForPause;
                        int totalCount = Integer.parseInt(countDownPause.getText().toString());

                        runOnUiThread(new Runnable()
                        {
                            @Override
                            public void run()
                            {
                                int totalCount = Integer.parseInt(countDownPause.getText().toString());
                                countDownText.setText(String.valueOf(totalCount));
                            }
                        });

                        timerForPause = new CountDownTimer((totalCount+1) * 1000, 1000)
                        {
                            Boolean firstTick = false;
                            public void onTick(long millisUntilFinished)
                            {
                                // onTick gets called at start, but I don't need it to run till the next tick
                                if (firstTick == true)
                                {
                                    runOnUiThread(new Runnable()
                                    {
                                        @Override
                                        public void run()
                                        {
                                            int currentCount = Integer.parseInt(countDownText.getText().toString());
                                            --currentCount;
                                            countDownText.setText(String.valueOf(currentCount));
                                        }
                                    });
                                }
                                else
                                    firstTick = true;
                            }

                            public void onFinish()
                            {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run()
                                    {
                                        // Pause everything
                                        isOnGoing = false;
                                        mMediaRecorderCaptureVideo.pause();
                                        mMediaPlayerAudioFile.pause();

                                        // Allow for stopping
                                        recordStopButton.setEnabled(true);

                                        // Allow for resume
                                        resumeButton.setEnabled(true);

                                        // Change the numbers allowed
                                        countDownStart.setEnabled(true);
                                        countDownPause.setEnabled(true);
                                        audioSpeed.setEnabled(true);
                                    }
                                });
                            }
                        }.start();

                        runOnUiThread(new Runnable()
                        {
                            @Override
                            public void run()
                            {
                                // Camera is available and unlocked, MediaRecorder is prepared,
                                // now you can start recording
                                mMediaRecorderCaptureVideo.start();
                                mMediaPlayerAudioFile.start();
                                isRecording = true;
                                isOnGoing = true;
                            }
                        });
                    }
                }.start();
            }
        }
    }

}