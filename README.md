# MediaRecorder_mp4parser_Usage
********************************************** DISCLAIMER *******************************************************************   
This Android Studio / Android App uses minimal error checking and barely any "Best-Practices", use as a starting point. 
MediaRecorder &amp; mp4parser usage, to mux (merge) captured front-facing camera with user selected audio file (m4a) into an mp4 file. 
The audio file's playback speed can be adjusted and the captured video file will be adjusted to respect the audio playback speed.

********************************************** BACKGROUND ******************************************************************* 
My wife loves and uses "Tik-Tok" (https://www.tiktok.com/) every day. And it has some impressive video editing features,
but only allows for 15-seconds of video capture and editing, unless you upload your own video, then it can be as long as you want (?).
So, she asked me: I need an App that can record me while playing an audio file at my desired playback speed. Then the App needs to 
merge the captured video and the audio file together, respecting the audio playback speed.

********************************************** TECHNICAL DETAILS *************************************************************
- Uses the Camera (deprecated) to get a handle to the front-facing camera. 
- Uses the MediaRecorder to capture the video and save into a mp4 file. 
- Uses the MediaPlayer to playback the chosen audio file (m4a only) at the desired playback speed 0 - 1 - 2 (1 is normal).
- Uses mp4parser (https://github.com/sannies/mp4parser) to edit the video file: Change the speed of the video, change the length of the
video. 
- Uses mp4parser (https://github.com/sannies/mp4parser) to edit the audio file: Trim the audio file to match the final length of
the video file. 
- Uses mp4parser (https://github.com/sannies/mp4parser) to mux (merge) the audio track and the video file into a single mp4 file. 

