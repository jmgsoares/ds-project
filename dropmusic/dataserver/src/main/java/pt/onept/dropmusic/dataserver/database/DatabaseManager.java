package pt.onept.dropmusic.dataserver.database;

import pt.onept.dropmusic.common.exception.IncompleteException;
import pt.onept.dropmusic.common.exception.NotFoundException;
import pt.onept.dropmusic.common.exception.UnauthorizedException;
import pt.onept.dropmusic.common.server.contract.type.*;

import java.io.InvalidClassException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class DatabaseManager {
	private DatabaseConnector dbConnector;
	private static Map<Class, String> objectTable = null;
	private static Map<Class, String> popTable = null;
	private static final Object lock = new Object();

	private static Map<Class, String> initObjectTable() {
		Map<Class, String> c2s = new HashMap<>();
		c2s.put(Album.class, "album");
		c2s.put(Artist.class, "artist");
		c2s.put(Upload.class, "upload");
		c2s.put(Music.class, "music");
		c2s.put(Notification.class, "notification");
		c2s.put(Review.class, "review");
		c2s.put(User.class, "account");
		return c2s;
	}

	private static Map<Class, String> initPopTable() {
		Map<Class, String> queries = new HashMap<>();
		queries.put(Album.class, "SELECT al.* FROM album al LEFT JOIN artist_album aa on al.id = aa.alb_id WHERE aa.id = ?");
		queries.put(Review.class, "SELECT * FROM review r WHERE r.alb_id = ?");
		queries.put(Music.class, "SELECT * FROM music m WHERE m.alb_id = ?");
		queries.put(Notification.class, "SELECT * FROM notification n WHERE n.use_id = ?");
		return queries;
	}

	public static String getTable(Class cls) {
		if(objectTable == null) {
			synchronized (lock) {
				if(objectTable == null) DatabaseManager.objectTable = DatabaseManager.initObjectTable();
			}
		}
		return objectTable.get(cls);
	}

	public static String getPopQuery(Class cls) {
		if(popTable == null) {
			synchronized (lock) {
				if(popTable == null) DatabaseManager.popTable = DatabaseManager.initPopTable();
			}
		}
		return popTable.get(cls);
	}

	public <T extends DropmusicDataType> PreparedStatement getInsertStatement(Connection connection, Class<T> tClass, T object) throws SQLException, InvalidClassException {
		PreparedStatement ps;
		if( object instanceof Album ) {
			Album album = (Album) object;
			ps = connection.prepareStatement("SELECT * FROM add_album(?, ?, ?);");
			ps.setInt(1, album.getArtist().getId());
			ps.setString(2, album.getName());
			ps.setString(3, album.getDescription());
		} else if( object instanceof Artist ) {
			Artist artist = (Artist) object;
			ps = connection.prepareStatement("INSERT INTO artist(name) VALUES(?) RETURNING *;");
			ps.setString(1, artist.getName());
		} else if( object instanceof Upload ) {
			Upload upload = (Upload) object;
			ps = connection.prepareStatement(""); //TODO
		} else if( object instanceof Music ) {
			Music music = (Music) object;
			ps = connection.prepareStatement("INSERT INTO music(alb_id, name) VALUES (?, ?) RETURNING *;");
			ps.setInt(1, music.getAlbumId());
			ps.setString(2, music.getName());
		} else if( object instanceof Notification ) {
			Notification notification = (Notification) object;
			ps = connection.prepareStatement("INSERT INTO notification(use_id, message) VALUES(?, ?) RETURNING *;");
			ps.setInt(1, notification.getUserId());
			ps.setString(2, notification.getMessage());
		} else if( object instanceof Review ) {
			Review review = (Review) object;
			ps = connection.prepareStatement("INSERT INTO review(alb_id, text, score) VALUES(?, ?, ?) RETURNING *;");
			ps.setInt(1,review.getAlbumId());
			ps.setString(2, review.getReview());
			ps.setFloat(3, review.getScore());
		} else if( object instanceof User ) {
			User user = (User) object;
			ps = connection.prepareStatement("INSERT INTO account(name, password) VALUES (?, ?) RETURNING *;");
			ps.setString(1, user.getUsername());
			ps.setString(2, user.getPassword());

		} else {
			System.out.println("###" + TypeFactory.getSubtype(object).toString());
			throw new InvalidClassException("");
		}
		return ps;
	}

	public DatabaseManager(DatabaseConnector dbConnector) {
		this.dbConnector = dbConnector;
	}

// THESE FUNCTIONS GET CALLED BY THE HANDLER!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! LISADHJLA SHDLAS HDKLAJSHD OIASD

	public User login(User user) throws UnauthorizedException, SQLException {
		try(
			Connection dbConnection = this.dbConnector.getConnection();
			PreparedStatement ps = dbConnection.prepareStatement("SELECT * FROM account WHERE name = ? AND password = ?")
		) {
			ps.setString(1, user.getUsername());
			ps.setString(2, user.getPassword());
			ResultSet rs = ps.executeQuery();
			if(rs.next()) {
				return TypeFactory.constructType(User.class, rs);
			} else {
				throw new UnauthorizedException();
			}
		}
	}

	public <T extends DropmusicDataType> T insert(Class<T> tClass, T object) throws SQLException, InvalidClassException, IncompleteException {
		String tableName = DatabaseManager.getTable(tClass);
		try (
			Connection dbConnection = this.dbConnector.getConnection();
			PreparedStatement ps = this.getInsertStatement(dbConnection, tClass, object)
		){
			ResultSet rs = ps.executeQuery();
			if(rs.next()) return TypeFactory.constructType(tClass, rs);
			else throw new IncompleteException();
		}
	}

	public <T extends DropmusicDataType> T read(Class<T> tClass, int id) throws SQLException, NotFoundException {
		String tableName = DatabaseManager.getTable(tClass);
		try (
				Connection dbConnection = this.dbConnector.getConnection();
				PreparedStatement ps = dbConnection.prepareStatement("SELECT * FROM " + tableName + " WHERE id = ?")
		){
			ps.setInt(1, id);
			ResultSet rs = ps.executeQuery();
			if(rs.next()) {
				T object = TypeFactory.constructType(tClass, rs);
				if( tClass.equals(Artist.class) || tClass.equals(Album.class) ) populate(tClass, object);
				return object;
			} else {
				throw new NotFoundException();
			}
		}
	}

	public <T extends DropmusicDataType> List<T> readList(Class<T> tClass, DropmusicDataType object) throws SQLException {
				List<T> list = new LinkedList<>();
		try (
			Connection connection = dbConnector.getConnection();
			PreparedStatement ps = connection.prepareStatement(DatabaseManager.getPopQuery(tClass))
		) {
			ps.setInt(1, object.getId());
			ResultSet rs = ps.executeQuery();
			while (rs.next()) list.add(TypeFactory.constructType(tClass, rs));
		}
		return list;
	}

	private  <T extends DropmusicDataType> void populate(Class<T> tClass, T object) throws SQLException, NotFoundException {
		if( object instanceof Artist ) {
			Artist artist = (Artist) object;
			artist.setAlbums(this.readList(Album.class, artist));
		} else if( object instanceof Album) {
			Album album = (Album) object;
			album.setReviews(this.readList(Review.class, album))
				.setMusics(this.readList(Music.class, album));
		}
	}

}