package es.afm.microblog.utils;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import redis.clients.jedis.Jedis;

/**
 * Conexión a la redis. Implementa las funciones de nuestro sistema de microblog
 * sobre la redis
 * 
 */
public class Redis {

	private static Redis instance = null;
	private Jedis jedis;

	private static String SEP = "-------------------------";

	/**
	 * Constructor privado: Singleton. Crea la conexión con al redis
	 */
	private Redis() {
		jedis = new Jedis("localhost");
	}

	/**
	 * Singleton
	 * 
	 * @return devuelve la única instancia de la clase
	 */
	public static Redis getInstance() {
		if (instance == null)
			instance = new Redis();
		return instance;
	}

	/**
	 * Cierra la conexion a la redis
	 */
	public void close() {
		if (jedis != null)
			jedis.close();
		jedis = null;
		instance = null;
	}

	/**
	 * Crea un nuevo post. Crea un nuevo hashmap con el siguiente id de post con
	 * las propiedades body, userId y time. Para cada seguidor del usuario
	 * actualiza su timeline con la referencia al nuevo post. Por último se
	 * actualiza el timeline general
	 * 
	 * @param userId
	 *            del usuario que crea el post
	 * @param body
	 *            el texto del post
	 */
	public void newPost(String userId, String body) {
		// Obtener siguiente identificador de postId
		String postId = String.valueOf(jedis.incr("post_id"));

		Map<String, String> hash = new HashMap<String, String>();
		hash.put("body", body);
		hash.put("userId", userId);
		hash.put("time", String.valueOf(new Date().getTime()));
		// crear nuevo post
		jedis.hmset("post:" + postId, hash);
		// actualizar los timelines de cada usuario
		Set<String> followers = jedis.zrange("followers:" + userId, 0, -1);
		// todo el mundo se sigue virtualmente a si mismo
		followers.add(userId);
		for (String follower : followers) {
			jedis.lpush("posts:" + follower, postId);
		}
		// actualizar el timeline
		jedis.lpush("timeline", postId);
		jedis.ltrim("timeline", 0, 10);
	}

	/**
	 * Muestra el timeline del usuario cuyo userId se pasa como parámetro
	 * 
	 * @param userId
	 *            userId del usuario que se quiere mostrar su timeline
	 * @param start
	 *            offset de inicio
	 * @param count
	 *            cuantos post se quieren mostrar
	 */
	public void showUserPosts(String userId, int start, int count) {
		List<String> posts = jedis.lrange("posts:" + userId, start, start + count);
		for (String post : posts) {
			showPost(post);
			IO.getIO().write(SEP);
		}
	}

	/**
	 * A través del postId, obtiene el map de propiedades del post y los muestra
	 * 
	 * @param postId
	 *            el identificador del post que se quiere mostrar por pantalla
	 */
	private void showPost(String postId) {
		// Obtener el post
		Map<String, String> map = jedis.hgetAll("post:" + postId);
		IO.getIO().write("Username: " + getUserName(map.get("userId")));
		IO.getIO().write("Body: " + map.get("body"));
		IO.getIO().write("Time: " + new Date(Long.parseLong(map.get("time"))).toString());
	}

	/**
	 * Muestra el timeline completo. Para ello consulta la lista definida como
	 * timeline
	 */
	public void showTimeline() {
		// obtener el timeline completo
		List<String> posts = jedis.lrange("timeline", 0, -1);
		for (String post : posts) {
			showPost(post);
			IO.getIO().write(SEP);
		}
	}

	/**
	 * Devuelve el username del userId recibido como parámetro
	 * 
	 * @param userId
	 *            el userId del que retornaremos el nombre
	 * @return el userId correspondiente al userId
	 */
	public String getUserName(String userId) {
		return jedis.hget("users:" + userId, "userName");
	}

	/**
	 * Un usuario sigue a otro. Actualiza los set de seguidos y seguidores
	 * (followed y followers)
	 * 
	 * @param currentUser
	 *            el userId del usuario en sesión
	 * @param userId
	 *            el userId del usuario a seguir
	 */
	public void follow(String currentUser, String userId) {
		jedis.zadd("followed:" + currentUser, new Date().getTime(), userId);
		jedis.zadd("followers:" + userId, new Date().getTime(), currentUser);
	}

	/**
	 * Dado un nombre de usuario devuelve su userid
	 * 
	 * @param userName
	 *            nombre de usuario del que queremos obtener el id
	 * @return el id del nombre de usuario que se recibe como parámetro. NULL si
	 *         no existe
	 */
	public String getUserIdFromUsername(String userName) {
		return jedis.hget("users", userName);
	}

	/**
	 * Incrementa el valor del siguiente userId y crea un nuevo map con los
	 * datos del usuario asignandolo al userId creado. De momento solo es
	 * userName pero podria tener contraseña, biografia, email, etc.
	 * 
	 * También será interesante a veces buscar al usuario por su nombre de
	 * usuario por lo que guardamos en el conjunto users el mapeo entre nombre
	 * de usuario y su userId
	 * 
	 * @param userName
	 *            El nombre del usuario
	 * @return El userId asignado
	 */
	public String newUser(String userName) {
		Long userId = jedis.incr("userId");
		Map<String, String> map = new HashMap<String, String>();
		map.put("userName", userName);
		jedis.hmset("users:" + userId, map);
		jedis.hset("users", userName, String.valueOf(userId));

		return String.valueOf(userId);
	}

	/**
	 * Esta función debe mostrar los seguidores que tienen en común los 2
	 * usuarios
	 * 
	 * ZINTERSTORE crea la intersección de 2 sets (la salida es otro set). Se
	 * debe usar sobre el conjunto de followers de los 2 usuarios
	 * 
	 * @param currentUser
	 *            id del usuario en sesión
	 * @param userId
	 *            id del otro usuario
	 */
	public void showFollowersInCommon(String currentUser, String userId) {
		jedis.zinterstore("common", "followers:" + currentUser, "followers:" + userId);
		Set<String> followers = jedis.zrange("common", 0, -1);
		if (followers.isEmpty())
			IO.getIO().write("No hay seguidores en común");
		else
			IO.getIO().write("Seguidores en común");
		for (String follower : followers) {
			IO.getIO().write(getUserName(follower));
		}

	}
}
