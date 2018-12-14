package pt.onept.sd1819.dropmusic.web.action;

import com.opensymphony.xwork2.Action;
import com.opensymphony.xwork2.ActionSupport;
import com.opensymphony.xwork2.ModelDriven;
import org.apache.struts2.interceptor.SessionAware;
import pt.onept.sd1819.dropmusic.common.exception.DataServerException;
import pt.onept.sd1819.dropmusic.common.exception.DuplicatedException;
import pt.onept.sd1819.dropmusic.common.exception.IncompleteException;
import pt.onept.sd1819.dropmusic.common.exception.UnauthorizedException;
import pt.onept.sd1819.dropmusic.common.server.contract.subcontract.UserManagerInterface;
import pt.onept.sd1819.dropmusic.common.server.contract.type.User;
import pt.onept.sd1819.dropmusic.web.communication.CommunicationManager;

import java.rmi.RemoteException;
import java.util.Map;

public class UserAction extends ActionSupport implements SessionAware, ModelDriven<User> {
	private User user = new User();
	private Map<String, Object> session;

	public String login() throws Exception {
		try {
			UserManagerInterface userManager = CommunicationManager.getServerInterface().user();
			this.user = userManager.login(this.user);
			this.session.put("user", this.user);
			this.session.put("logged", true);
			addActionMessage("Welcome " + user.getUsername());
			return Action.SUCCESS;
		} catch (RemoteException | DataServerException e) {
			e.printStackTrace();
			addActionError("There was an error around here");
		} catch (UnauthorizedException e) {
			addActionError("Wrong credentials");
		}
		this.session.remove("user");
		this.session.put("logged", false);
		return Action.ERROR;
	}

	public String logout() throws Exception {
		this.session.clear();
		return Action.SUCCESS;
	}

	public String register() throws Exception {
		try {
			UserManagerInterface userManager = CommunicationManager.getServerInterface().user();
			userManager.create(user, null);
			addActionMessage("User " + user.getUsername() + " created successfully");
			return Action.SUCCESS;
		} catch (DuplicatedException e) {
			addActionError("User " + user.getUsername() + " already exists");
		} catch (IncompleteException e) {
			addActionError("Some data fields were left empty");
		} catch (RemoteException | UnauthorizedException | DataServerException e) {
			e.printStackTrace();
			addActionError("There was an error around here");
		}
		return Action.ERROR;
	}

	public User getModel() {
		return this.user;
	}

	@Override
	public void setSession(Map<String, Object> map) {
		this.session = map;
	}
}