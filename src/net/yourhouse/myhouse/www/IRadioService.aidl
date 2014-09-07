package net.yourhouse.myhouse.www;

interface IRadioService {
	String errors();
	String showName();
	void start();
    void stop();
	int state();
}