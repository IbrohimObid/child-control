package uz.ajib.bolanazorat;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;

/** Qurilma administratori: yoqilgan bo'lsa, ilovani to'g'ridan-to'g'ri o'chirib bo'lmaydi. */
public class AdminReceiver extends DeviceAdminReceiver {
    @Override
    public CharSequence onDisableRequested(Context context, Intent intent) {
        return "Bu ota-ona nazorati. O'chirish uchun ota-onangdan ruxsat so'ra.";
    }
}
