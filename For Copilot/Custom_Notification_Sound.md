Custom notification sound:-

When an advertisement is detected from the whitelisted app , a small notification sound is played to alert the user that an app is playing.

This can be done through using the Custom notification alert method, below are the following ways to execute it

🏗️ Recommended Structure
Channel 1: "start_channel"
Sound: “start tone” (short beep or alert)
Channel 2: "end_channel"
Sound: “end tone” (different tone, softer or distinct)
🔧 1. Create both channels
void createChannels() {
NotificationManager manager = getSystemService(NotificationManager.class);

    createChannel("start_channel", "Start Alert", R.raw.start_sound);
    createChannel("end_channel", "End Alert", R.raw.end_sound);
}

void createChannel(String id, String name, int soundRes) {
Uri soundUri = Uri.parse("android.resource://" + getPackageName() + "/" + soundRes);

    AudioAttributes audioAttributes = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .build();

    NotificationChannel channel = new NotificationChannel(
            id,
            name,
            NotificationManager.IMPORTANCE_HIGH
    );

    channel.setSound(soundUri, audioAttributes);

    NotificationManager manager = getSystemService(NotificationManager.class);
    manager.createNotificationChannel(channel);
}


🔔 2. Trigger START sound


NotificationCompat.Builder builder =
new NotificationCompat.Builder(this, "start_channel")
.setSmallIcon(R.drawable.ic_notification)
.setContentTitle("Ad Started")
.setContentText("Ad has begun")
.setAutoCancel(true);

NotificationManagerCompat.from(this).notify(1, builder.build());
🔕 3. Trigger END sound
NotificationCompat.Builder builder =
new NotificationCompat.Builder(this, "end_channel")
.setSmallIcon(R.drawable.ic_notification)
.setContentTitle("Ad Ended")
.setContentText("Ad finished")
.setAutoCancel(true);

NotificationManagerCompat.from(this).notify(2, builder.build());


Making them silent visually but audible only

.setPriority(NotificationCompat.PRIORITY_LOW)
.setVisibility(NotificationCompat.VISIBILITY_SECRET)
