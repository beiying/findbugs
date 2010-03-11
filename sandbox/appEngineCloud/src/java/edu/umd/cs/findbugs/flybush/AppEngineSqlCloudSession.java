package edu.umd.cs.findbugs.flybush;

import com.google.appengine.api.datastore.Key;

import javax.jdo.annotations.IdentityType;
import javax.jdo.annotations.PersistenceCapable;
import javax.jdo.annotations.Persistent;
import javax.jdo.annotations.PrimaryKey;
import java.util.Date;

@PersistenceCapable(identityType = IdentityType.APPLICATION)
public class AppEngineSqlCloudSession implements SqlCloudSession {

    @Persistent
    @PrimaryKey
    private String randomID;

    @Persistent private Key user;
    @Persistent private Date date;
    @Persistent private Key invocation;

    public AppEngineSqlCloudSession(Key author, String randomID, Date date) {
        this.user = author;
        this.randomID = randomID;
        this.date = date;
    }

    public AppEngineSqlCloudSession(Key author, long randomID, Date date) {
        this(author, Long.toString(randomID), date);
    }

    public Key getUser() {
		return user;
	}

	public Key getInvocation() {
		return invocation;
	}

    public void setInvocation(DbInvocation invocation) {
        this.invocation = ((AppEngineDbInvocation)invocation).getKey();
    }

    public String getRandomID() {
        return randomID;
    }

    @Override
	public String toString() {
		return "SqlCloudSession [date=" + date + ", invocation=" + invocation
				+ ", randomID=" + randomID + ", user=" + user
				+ "]";
	}
}