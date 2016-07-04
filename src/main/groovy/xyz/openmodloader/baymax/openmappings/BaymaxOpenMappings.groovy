package xyz.openmodloader.baymax.openmappings

import com.typesafe.config.Config
import net.dv8tion.jda.events.message.MessageReceivedEvent
import net.shadowfacts.baymax.Baymax
import net.shadowfacts.baymax.command.CommandManager
import net.shadowfacts.baymax.command.exception.WrongUsageException
import net.shadowfacts.baymax.module.base.Module
import org.apache.commons.logging.LogFactory
import org.kohsuke.github.GHRepository
import org.kohsuke.github.GitHub
import org.reflections.Reflections
import org.reflections.util.ConfigurationBuilder

import java.util.stream.Collectors

/**
 * @author shadowfacts
 */
class BaymaxOpenMappings extends Module {

	public static final def USAGE = "Adds a request to map a class/field/method/parameter in OpenMappings to the queue or sends the request. Usage: openmap add <c(lass)|f(ield)|m(ethod)|p(aram)> <obf> <new> OR openamp request OR openmap clear"

	private static final def logger = LogFactory.getLog(BaymaxOpenMappings.class)

	private def pending = new HashMap<String, List<Remap>>()

	private String username
	private String password

	private GitHub gh
	private GHRepository repo

	BaymaxOpenMappings() {
		super("OpenMappings")
	}

	@Override
	void configure(Config config) {
		super.configure(config)
		username = config.getString("OpenMappings.username")
		password = config.getString("OpenMappings.password")
	}

	@Override
	void init() {
		logger.info("Initializing OpenMappings plugin")

		gh = GitHub.connectUsingPassword(username, password)
		repo = gh.getRepository("OpenModLoader/OpenMappings")

		CommandManager.register("openmap", USAGE, this.&handle)
	}

	private void handle(MessageReceivedEvent event, String[] args) {
		if (args.length == 0) {
			throw new WrongUsageException(USAGE)
		}

		def operation = args[0].toLowerCase()
		if (operation == "add") {
			add(event, Arrays.copyOfRange(args, 1, args.length))
		} else if (operation == "request") {
			request(event)
		} else if (operation == "clear") {
			clear(event)
		} else {
			throw new WrongUsageException(USAGE)
		}
	}

	private void add(MessageReceivedEvent event, String[] args) {
		if (args.length != 3) {
			throw new WrongUsageException(USAGE)
		}

		def sender = event.author.id

		if (!pending.containsKey(sender)) pending.put(sender, new ArrayList<>())

		pending.get(sender).add(new Remap(Type.get(args[0]), args[1], args[2]))
		event.channel.sendMessage("Added remap to queue")
	}

	private void request(MessageReceivedEvent event) {
		def sender = event.author.id

		if (!pending.containsKey(sender)) {
			throw new WrongUsageException("Cannot request mappings when none have been added to the queue")
		}

		def lines = new ArrayList<String>()
		def remaps = pending.get(sender)

		lines.add("Classes:")
		lines.addAll(remaps.stream()
			.filter({ it.type == Type.CLASS })
			.map({ it.createLine() })
			.collect(Collectors.toList()))
		lines.add("")
		lines.add("Fields:")
		lines.addAll(remaps.stream()
			.filter({ it.type == Type.FIELD })
			.map({ it.createLine() })
			.collect(Collectors.toList()))
		lines.add("")
		lines.add("Methods:")
		lines.addAll(remaps.stream()
			.filter({ it.type == Type.METHOD })
			.map({ it.createLine() })
			.collect(Collectors.toList()))
		lines.add("")
		lines.add("Parameters:")
		lines.addAll(remaps.stream()
			.filter({ it.type == Type.PARAM })
			.map({ it.createLine() })
			.collect(Collectors.toList()))
		lines.add("")
		lines.add("")
		lines.add(String.format("Requested by: %s (ID #%s)", getSenderName(event), sender))

		def issue = repo.createIssue("Remap requests from " + getSenderName(event)).body(String.join("\n", lines)).label("bot").create()
		event.channel.sendMessage("Remaps requested, see: " + issue.htmlUrl)
	}

	private void clear(MessageReceivedEvent event) {
		if (pending.containsKey(event.author.id)) {
			pending.remove(event.author.id)
			event.channel.sendMessage("Queue cleared")
		}
	}

	private static String getSenderName(MessageReceivedEvent event) {
		return event.guild == null ? event.author.username : event.guild.getNicknameForUser(event.author) == null ? event.author.username : event.guild.getNicknameForUser(event.author)
	}

	private static enum Type {
		CLASS("Class"),
		FIELD("Field"),
		METHOD("Method"),
		PARAM("Parameter");

		private String name

		Type(String name) {
			this.name = name
		}

		static Type get(String s) {
			s = s.toLowerCase()
			if (s == "c" || s == "class") return CLASS
			else if (s == "f" || s == "field") return FIELD
			else if (s == "m" || s == "method") return METHOD
			else if (s == "p" || s == "param" || s == "parameter") return PARAM
			else throw new WrongUsageException("Invalid type " + s)
		}
	}

	private static class Remap {
		private Type type
		private String obf
		private String deobf

		Remap(Type type, String obf, String deobf) {
			this.type = type
			this.obf = obf
			this.deobf = deobf
		}

		private String createLine() {
			return String.format("`%s` -> `%s`", obf, deobf)
		}
	}

}
