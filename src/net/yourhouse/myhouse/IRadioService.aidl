package net.yourhouse.myhouse;

interface IRadioService {
	String errors();
	String showName();
	void start();
    void stop();
	int state();
}
