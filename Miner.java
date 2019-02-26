import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

//TEACA BOGDAN

/**
 * Class for a miner.
 */
public class Miner extends Thread {
	/**
	 * Creates a {@code Miner} object.
	 * 
	 * @param hashCount
	 *            number of times that a miner repeats the hash operation when
	 *            solving a puzzle.
	 * @param solved
	 *            set containing the IDs of the solved rooms
	 * @param channel
	 *            communication channel between the miners and the wizards
	 */
	
	Integer hashCount;
	TreeSet<Integer> solved;
	CommunicationChannel channel;
	
	ConcurrentHashMap<Integer, ArrayBlockingQueue<Message>> map;
	
	public Miner(Integer hashCount, Set<Integer> solved, CommunicationChannel channel){
		
		// salvam datele date ca parametru constructorului
		
		this.hashCount = hashCount;
		this.solved = new TreeSet<Integer>();
		this.channel = channel;
	}

	@Override
	public void run(){
		while(true){
			
			// salvam primul mesaj din perechea de mesaje pe care o primeste
			// minerul curent, acest prim mesaj continand informatiile despre
			// camera parinte
			
			Message parentRoomMessage = channel.getMessageWizardChannel();
			
			// daca mesajul primit este null inseamna ca in acest moment nu
			// exista mesaje in buffer-ul de mesaje al canalului vrajitorilor,
			// si deci vom cicla in bucla while pana cand gasim mesaje in
			// buffer
			
			if(parentRoomMessage == null) continue;
			
			// salvam al doilea mesaj din perechea de mesaje pe care o primeste
			// minerul curent, acest al doilea mesaj continand informatiile despre
			// camera curenta
			
			Message currentRoomMessage = channel.getMessageWizardChannel();
			
			// daca mesajul primit este null inseamna ca in acest moment nu
			// exista mesaje in buffer-ul de mesaje al canalului vrajitorilor,
			// si deci vom cicla in bucla while pana cand gasim mesaje in
			// buffer
			
			if(currentRoomMessage == null) continue;
			
			// calculam hash-ul hash-ului hash-ului ... hash-ului mesajului curent
			// de hashCount numar de ori, hash-ul rezultat reprezentand solutia
			// pentru camera curenta
			
			String newHash = encryptMultipleTimes(currentRoomMessage.getData(), hashCount);

			// salvam ID-ul camerei curente (camera a carei am gasit solutia) in
			// set-ul "solved" primit ca parametru, care contine ID-urile camerelor
			// carora le-am gasit solutia
			
			solved.add(currentRoomMessage.getCurrentRoom());
			
			// cream un mesaj nou in care punem ID-ul camerei parinte, ID-ul camerei
			// curente si hash-ul tocmai calculat (solutia camerei curente)
			
			Message newMessage = new Message(parentRoomMessage.getCurrentRoom(),
			                                 currentRoomMessage.getCurrentRoom(),
			                                 newHash);

			// trimitem mesajul nou creat pe canalul minerilor, astfel incat
			// vrajitorii sa preia acest mesaj cu care sa deschida camere noi
			
			channel.putMessageMinerChannel(newMessage);
		}
	}
	
	private static String encryptMultipleTimes(String input, Integer count) {
        String hashed = input;
        for (int i = 0; i < count; ++i) {
            hashed = encryptThisString(hashed);
        }

        return hashed;
    }

    private static String encryptThisString(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] messageDigest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            
            // convert to string
            StringBuffer hexString = new StringBuffer();
            for (int i = 0; i < messageDigest.length; i++) {
            String hex = Integer.toHexString(0xff & messageDigest[i]);
            if(hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
    
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
