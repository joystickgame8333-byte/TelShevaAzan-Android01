package com.example.telshevaazan;

final class QiblaCalculator {
    private static final double TEL_SHEVA_LATITUDE = 31.24864;
    private static final double TEL_SHEVA_LONGITUDE = 34.86007;
    private static final double KAABA_LATITUDE = 21.422487;
    private static final double KAABA_LONGITUDE = 39.826206;

    private QiblaCalculator() {}

    static double telShevaBearing() {
        return bearing(TEL_SHEVA_LATITUDE, TEL_SHEVA_LONGITUDE, KAABA_LATITUDE, KAABA_LONGITUDE);
    }

    static double delta(double heading, double bearing) {
        double value = bearing - heading;
        while (value > 180) {
            value -= 360;
        }
        while (value < -180) {
            value += 360;
        }
        return value;
    }

    private static double bearing(double lat1, double lon1, double lat2, double lon2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double deltaLambda = Math.toRadians(lon2 - lon1);
        double y = Math.sin(deltaLambda) * Math.cos(phi2);
        double x = Math.cos(phi1) * Math.sin(phi2)
                - Math.sin(phi1) * Math.cos(phi2) * Math.cos(deltaLambda);
        return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360;
    }
}
