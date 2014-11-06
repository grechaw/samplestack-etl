import org.gradle.api.tasks.TaskAction

public class SamplestackPostService extends SamplestackService {

    SamplestackPostService() {
       super()
    }

    @TaskAction
    void getDoc() {
        threadedThings("question", "make-questions")
    }
}
