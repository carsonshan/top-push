package com.tmall.top.push.servlet;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;

import com.tmall.top.push.ClientManager;

public class InitServlet extends HttpServlet {
	@Override
	public void init(ServletConfig config) throws ServletException {
		ClientManager.Init(config);
		//worker
		
		super.init(config);
	}
}