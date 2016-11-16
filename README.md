# HPBM
Software for the Hydro-Pack Bluetooth Monitor. The hardware has been described on the project's [Hackster site]( https://www.hackster.io/gholdys/hydro-pack-bluetooth-monitor-3da195)

The device is based on an [Adafruit Bluefruit LE Micro](https://learn.adafruit.com/bluefruit-le-micro-atmega32u4-microcontroller-usb-bluetooth-le-in-one/overview) and uses a [flow meter from Seeed](http://www.seeedstudio.com/depot/G14-Water-Flow-Sensor-p-1345.html). 

The consumption data are send over Bluetooth to an Android device. The device code is located in the HPBM-Device directory, while the client part can be found in the HPBM-App directory. The HPBM-DeviceSim directory contains a version of the Device code that simulates the HPBM-Device. It does not read any data from sensors so can only be used for development and testing.
