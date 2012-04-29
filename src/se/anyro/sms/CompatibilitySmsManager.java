/*
 * Copyright (C) 2012 Adam Nybäck
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package se.anyro.sms;

import java.lang.reflect.Method;
import java.util.ArrayList;

import android.app.PendingIntent;
import android.telephony.SmsManager;

/**
 * Help class than mimics the interfaces of {@link android.telephony.SmsManager} and
 * {@link android.telephony.gsm.SmsManager} and uses the best one for each device. There are also two work-arounds to
 * prevent double messages being sent on Samsung Galaxy S2 with Android 4.0.3 and HTC Tattoo.
 */
public abstract class CompatibilitySmsManager {

    private static CompatibilitySmsManager sInstance;

    /**
     * See {@link android.telephony.SmsManager#divideMessage(String)}
     */
    public abstract ArrayList<String> divideMessage(String text);

    /**
     * See {@link android.telephony.SmsManager#sendTextMessage(String, String, String, PendingIntent, PendingIntent)}
     */
    public abstract void sendTextMessage(String destinationAddress, String scAddress, String text,
            PendingIntent sentIntent, PendingIntent deliveryIntent);

    /**
     * See
     * {@link android.telephony.SmsManager#sendMultipartTextMessage(String, String, ArrayList, ArrayList, ArrayList)}
     */
    public abstract void sendMultipartTextMessage(String destinationAddress, String scAddress, ArrayList<String> parts,
            ArrayList<PendingIntent> sentIntents, ArrayList<PendingIntent> deliveryIntents);

    /**
     * See
     * {@link android.telephony.SmsManager#sendDataMessage(String, String, short, byte[], PendingIntent, PendingIntent)}
     */
    public abstract void sendDataMessage(String destinationAddress, String scAddress, short destinationPort,
            byte[] data, PendingIntent sentIntent, PendingIntent deliveryIntent);

    public static CompatibilitySmsManager getDefault() {
        if (sInstance == null) {
            // Find the best match for the current device
            try {
                sInstance = new GalaxyS2IcsSmsManager();
            } catch (Throwable e) {
                try {
                    sInstance = new DonutSmsManager();
                } catch (Throwable ex) {
                    sInstance = new LegacySmsManager();
                }
            }
        }
        return sInstance;
    }

    /**
     * For Cupcake (1.5) and earlier Android versions.
     */
    @SuppressWarnings("deprecation")
    private static class LegacySmsManager extends CompatibilitySmsManager {

        private android.telephony.gsm.SmsManager mSmsManager = android.telephony.gsm.SmsManager.getDefault();

        @Override
        public ArrayList<String> divideMessage(String text) {
            return mSmsManager.divideMessage(text);
        }

        @Override
        public void sendTextMessage(String destinationAddress, String scAddress, String text, PendingIntent sentIntent,
                PendingIntent deliveryIntent) {
            mSmsManager.sendTextMessage(destinationAddress, scAddress, text, sentIntent, deliveryIntent);
        }

        @Override
        public void sendMultipartTextMessage(String destinationAddress, String scAddress, ArrayList<String> parts,
                ArrayList<PendingIntent> sentIntents, ArrayList<PendingIntent> deliveryIntents) {
            mSmsManager.sendMultipartTextMessage(destinationAddress, scAddress, parts, sentIntents, deliveryIntents);
        }

        @Override
        public void sendDataMessage(String destinationAddress, String scAddress, short destinationPort, byte[] data,
                PendingIntent sentIntent, PendingIntent deliveryIntent) {
            mSmsManager.sendDataMessage(destinationAddress, scAddress, destinationPort, data, sentIntent,
                    deliveryIntent);
        }
    }

    /**
     * For Donut (1.6) and later Android versions.
     */
    private static class DonutSmsManager extends CompatibilitySmsManager {

        protected SmsManager mSmsManager;

        // Class initialization fails when this throws an exception
        static {
            try {
                Class.forName("android.telephony.SmsManager");
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }

        private DonutSmsManager() {
            mSmsManager = SmsManager.getDefault();
        }

        @Override
        public ArrayList<String> divideMessage(String text) {
            return SmsManager.getDefault().divideMessage(text);
        }

        // Work-around mainly for HTC Tattoo
        @Override
        public void sendTextMessage(String destinationAddress, String scAddress, String text, PendingIntent sentIntent,
                PendingIntent deliveryIntent) {
            ArrayList<String> parts = divideMessage(text);
            ArrayList<PendingIntent> sentIntents = null;
            if (sentIntent != null) {
                sentIntents = new ArrayList<PendingIntent>();
                sentIntents.add(sentIntent);
            }
            ArrayList<PendingIntent> deliveryIntents = null;
            if (deliveryIntent != null) {
                deliveryIntents = new ArrayList<PendingIntent>();
                deliveryIntents.add(deliveryIntent);
            }
            sendMultipartTextMessage(destinationAddress, scAddress, parts, sentIntents, deliveryIntents);
        }

        @Override
        public void sendMultipartTextMessage(String destinationAddress, String scAddress, ArrayList<String> parts,
                ArrayList<PendingIntent> sentIntents, ArrayList<PendingIntent> deliveryIntents) {
            SmsManager.getDefault().sendMultipartTextMessage(destinationAddress, scAddress, parts, sentIntents,
                    deliveryIntents);
        }

        @Override
        public void sendDataMessage(String destinationAddress, String scAddress, short destinationPort, byte[] data,
                PendingIntent sentIntent, PendingIntent deliveryIntent) {
            SmsManager.getDefault().sendDataMessage(destinationAddress, scAddress, destinationPort, data, sentIntent,
                    deliveryIntent);
        }
    }

    /**
     * Special implementation for Samsung Galaxy S2 which has a nasty bug in the 4.0.3 update.
     * 
     * See {@link http://code.google.com/p/android/issues/detail?id=27024}
     */
    private static class GalaxyS2IcsSmsManager extends DonutSmsManager {

        private static Method method;

        static {
            try {
                Class<?> classSmsManager = Class.forName("android.telephony.SmsManager");

                Class<?> paramTypes[] = new Class[9];

                paramTypes[0] = String.class;
                paramTypes[1] = String.class;
                paramTypes[2] = ArrayList.class;
                paramTypes[3] = ArrayList.class;
                paramTypes[4] = ArrayList.class;
                paramTypes[5] = Boolean.TYPE;
                paramTypes[6] = paramTypes[7] = paramTypes[8] = Integer.TYPE;

                method = classSmsManager.getMethod("sendMultipartTextMessage", paramTypes);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }

        @Override
        public void sendMultipartTextMessage(String destinationAddress, String scAddress, ArrayList<String> parts,
                ArrayList<PendingIntent> sentIntents, ArrayList<PendingIntent> deliveryIntents) {
            Object args[] = new Object[9];
            args[0] = destinationAddress;
            args[1] = scAddress;
            args[2] = parts;
            args[3] = sentIntents;
            args[4] = deliveryIntents;
            args[5] = Boolean.valueOf(false);
            args[6] = args[7] = args[8] = Integer.valueOf(0);

            try {
                method.invoke(mSmsManager, args);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}