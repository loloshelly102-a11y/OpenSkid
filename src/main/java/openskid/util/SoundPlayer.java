package openskid.util;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import java.io.BufferedInputStream;
import java.io.InputStream;

public final class SoundPlayer {
    private SoundPlayer() {
    }

    public static void play(String resourcePath, int volumePct) {
        if (resourcePath == null || resourcePath.isEmpty()) {
            return;
        }
        int vol = Math.max(0, Math.min(100, volumePct));
        if (vol <= 0) {
            return;
        }
        final int fixedVol = vol;
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    InputStream raw = SoundPlayer.class.getResourceAsStream(resourcePath);
                    if (raw == null) {
                        return;
                    }
                    BufferedInputStream buffered = new BufferedInputStream(raw);
                    AudioInputStream stream = null;
                    Clip clip = null;
                    try {
                        stream = AudioSystem.getAudioInputStream(buffered);
                        clip = AudioSystem.getClip();
                        clip.open(stream);
                        try {
                            if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                                FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                                float db = (float) (20.0 * Math.log10(fixedVol / 100.0));
                                if (db < gain.getMinimum()) {
                                    db = gain.getMinimum();
                                }
                                if (db > gain.getMaximum()) {
                                    db = gain.getMaximum();
                                }
                                gain.setValue(db);
                            }
                        } catch (Exception ignored) {
                        }
                        clip.start();
                        long waitMs = clip.getMicrosecondLength() / 1000L + 200L;
                        if (waitMs > 0) {
                            try {
                                Thread.sleep(waitMs);
                            } catch (InterruptedException ignored) {
                            }
                        }
                    } finally {
                        try {
                            if (clip != null) {
                                clip.close();
                            }
                        } catch (Exception ignored) {
                        }
                        try {
                            if (stream != null) {
                                stream.close();
                            }
                        } catch (Exception ignored) {
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        });
        thread.setDaemon(true);
        thread.start();
    }
}
