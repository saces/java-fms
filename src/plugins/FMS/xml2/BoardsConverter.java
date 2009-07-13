package plugins.FMS.xml2;

import com.thoughtworks.xstream.converters.collections.CollectionConverter;
import com.thoughtworks.xstream.mapper.Mapper;
import com.thoughtworks.xstream.mapper.MapperWrapper;

public class BoardsConverter extends CollectionConverter {
	public BoardsConverter(Mapper mapper) {
		super(new MapperWrapper(mapper) {
			@Override
			@SuppressWarnings("unchecked")
			public String serializedClass(Class type) {
				if (String.class.equals(type))
					return "Board";
				return super.serializedClass(type);
			}

			@Override
			public Class<?> realClass(String elementName) {
				if ("Board".equals(elementName))
					return String.class;
				return super.realClass(elementName);
			}
		});
	}
}